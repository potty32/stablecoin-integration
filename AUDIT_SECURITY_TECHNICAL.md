# Sicherheits- & Compliance-Audit — Atruvia Stablecoin Integration Platform

> **Klassifizierung:** INTERN — STRENG VERTRAULICH  
> **Erstellt:** 2026-08-20  
> **Auditor-Rolle:** Lead IT-Auditor & Principal Security & Compliance Engineer, Atruvia AG  
> **Methodischer Rahmen:** Atruvia-BMAD-Standard, OWASP Top 10, OWASP ASVS 4.0, BaFin MaRisk AT 4.3.4, BaFin BAIT, GwG §43, EU TFR Reg. 2023/1113, FATF Recommendation 16, MiCA, WpHG/EStG §45a  
> **Code-Basis:** Commit `9a41bdc` — 232 Tests, Flyway V1–V20, Spring Boot 3.3.5 / Java 21 / PostgreSQL 16

---

## Executive Summary — Ampelstatus

| Kategorie | Status | Begründung |
|-----------|--------|------------|
| **Sicherheit (Security)** | 🔴 KRITISCH | 3 kritische IDOR/Auth-Lücken; keine RBAC; Admin-Endpunkte für jeden Nutzer zugänglich |
| **Zahlungsverkehr (Functional)** | 🔴 KRITISCH | FX-Konvertierung falsch (17 % Überkreditierung); Transaktionslimits nicht durchgesetzt; kein FIU-Meldeweg |
| **Technik (Technical)** | 🔴 KRITISCH | OutboxProcessor-Recovery funktioniert nicht (RLS-Kontext fehlt); Ledger-Booking ohne atomare Persistenz; kein SKIP LOCKED |
| **Regulatorik (Compliance)** | 🔴 KRITISCH | Travel Rule nicht durchgesetzt (Schwellwert 15.000 EUR statt €0; originator_name nie gesetzt); keine GwG-§43-FIU-Meldung |

> **Gesamturteil:** Die Plattform ist in ihrer aktuellen Form **nicht produktionsreif** für den regulierten Bankbetrieb. Es bestehen 9 kritische Befunde (PRIO HIGH), von denen 4 unmittelbar finanzielle Risiken (Überkreditierung, ungesicherte Limits, Buchungsverluste) und 5 unmittelbare Regulierungs-/Bußgeldrisiken (GwG, EU TFR, MiCA) darstellen.

---

## 1. Priorisierter Maßnahmenkatalog

### PRIO HIGH — Sofortmaßnahmen (vor Go-Live Pflicht)

| ID | Kategorie | Komponente | Beschreibung | Risiko | BMAD-Impact | Empfohlene Lösung |
|----|-----------|------------|-------------|--------|-------------|-------------------|
| **S-01** | Sicherheit | `JwtAuthFilter.java:48–58` | **Dev-Mode-Bypass ohne IP-Whitelist**: `devMode=true` gewährt jedem nicht-authentifizierten Request vollen Zugriff als `dev-user/tenant-default`. Kein Netzwerkschutz, kein Compile-Time-Guard. | Angreifer auf Staging/CI kann alle Endpoints ohne Token aufrufen | API-First: Token-Pflicht muss technisch erzwingbar sein | `devMode=true` nur in `@Profile("dev")`; IP-Whitelist auf `127.0.0.1` beschränken; `SecurityConfig` muss devMode-Bypass aus der eigentlichen Filterchain ausschließen |
| **S-02** | Sicherheit | `B2bController.java:280–320` | **Keine Rollenprüfung auf Admin-Endpunkten**: `/admin/kill-switch`, `/admin/reassign-transaction`, `/admin/sanctions-scan` sind für jeden authentifizierten Nutzer zugänglich. Jeder B2B-Kunde kann den globalen Kill-Switch aktivieren und alle Zahlungen einfrieren. | Vollständiger Zahlungsstopp aller 800 VR-Banken durch jeden JWT-Inhaber | Schichtenarchitektur: Autorisierung gehört in Security-Layer | `@PreAuthorize("hasRole('ADMIN')")` auf alle Admin-Methoden; ROLE_ADMIN über JWT-Claim `roles: ["ADMIN"]` steuern; `SecurityConfig` um `hasRole`-Regeln erweitern |
| **S-03** | Sicherheit | `B2bController.java:104–108` | **IDOR auf `GET /api/v1/b2b/transfers/{id}`**: Kein Ownership-Check. Innerhalb desselben Tenants kann Kunde A alle Transaktionen von Kunde B per UUID lesen (Betrag, Wallet, Hash). `CommonController.getTransaction()` hat korrekten Check (Zeile 82) — `B2bController.getTransfer()` hat keinen. | Datenschutzverletzung (DSGVO Art. 5), Bankgeheimnis | DTO-Kapselung: Service-Layer muss Ownership prüfen | `B2bTransferService.getById(id, customerId)` ergänzen; `accountRepository.findByCustomerId(auth.getName())` + Vergleich `tx.getCustomerAccount().getCustomerId()` analog `CommonController.java:80–83` |
| **F-01** | Zahlungsverkehr | `InboundProcessingService.java:213–214` | **FX-Konvertierung falsch — 17 % Überkreditierung**: `getEurUsdRate()` liefert EUR/USD ≈ 1,0823 ("1 EUR = 1,0823 USD"). Inbound: `amountEur = amountUsdc × 1.0823` → falsch. Muss `amountEur = amountUsdc / 1.0823` sein. Aktuell werden Kunden für 10.000 USDC mit 10.823 EUR statt ~9.239 EUR gutgeschrieben. | Direkter Finanzverlust pro Inbound-USDC-Transaktion; Bilanzverzerrung; MiCA Art. 36-Deckungslücke | DTO: FX-Berechnung gehört in FxRateService mit klarer API-Semantik | `FxRateService.getBaseRate(USDC)` semantisch umbauen: immer EUR pro 1 Stablecoin zurückgeben. Inbound: `amountEur = amountUsdc.multiply(baseRate)` nur korrekt wenn Rate = EUR/USDC. ECB-API liefert EUR/USD → Kehrwert bilden: `BigDecimal.ONE.divide(eurUsdRate, 8, HALF_UP)` |
| **F-02** | Zahlungsverkehr | `B2bTransferService.java:444–458` | **Single-Transaction-Limit nie durchgesetzt**: `LimitResolver.resolveSingleLimit()` existiert, wird aber nirgends aufgerufen. `TenantSettings.txLimitSingleB2b` (default 25.000 EUR) und `txLimitSingleB2c` (5.000 EUR) in DB vorhanden aber wirkungslos. 100 Mio.-EUR-Transfer möglich. | Regulatorisches Risiko (§3 GwG, PSD2 SCA-Limits); Reputationsrisiko; potenzielle MiCA-Verletzung | Strikte Schichtenarchitektur: Limit-Prüfung in `persistInitialTransaction()` | `limitResolver.resolveSingleLimit(account, settings)` in `persistInitialTransaction()` aufrufen; bei Überschreitung `TaurusLimitExceededException` werfen |
| **F-03** | Zahlungsverkehr | Alle Services | **Daily-Cumulative-Limit nie durchgesetzt**: `LimitResolver.resolveDailyLimit()` vorhanden, nie aufgerufen. `TenantSettings.txLimitDailyB2b/B2c` wirkungslos. B2C-Kunde kann unbegrenzt viele 5.000-EUR-Transfers am gleichen Tag ausführen. | GwG-Strukturierungsrisiko (§15 GwG); Regulatorisches Bußgeldrisiko | Schichtenarchitektur: Tages-Aggregat-Prüfung in Service vor Transaktion | `stablecoinTransactionRepository.sumSettledTodayByAccount(accountId, LocalDate.now())` implementieren; `resolveDailyLimit()` in `persistInitialTransaction()` aufrufen |
| **F-04** | Zahlungsverkehr | `ComplianceService.java:49–87` | **Kein FIU-Meldeweg bei AML-Block (GwG §43)**: Bei `COMPLIANCE_REJECTED` / `AML_INBOUND_BLOCK` wird nur `audit_log` beschrieben. Keine automatische Meldung an die FIU (FIU-Online-API). Gesetzliche Meldepflicht §43 Abs. 1 GwG: unverzügliche Verdachtsmeldung. | Strafbarkeit nach §56 Abs. 1 Nr. 70 GwG (Bußgeld bis 5 Mio. EUR oder 10 % Jahresumsatz) | Strikte Isolation: Compliance-Event gehört in Outbox → async FIU-Client | `FiuReportingService` implementieren mit Outbox-Pattern; `COMPLIANCE_BLOCK_SAR_REQUIRED`-Event in outbox_message schreiben; dedizierter `HttpFiuOnlineClient` für §43-GwG-Meldung |
| **T-01** | Technik | `OutboxProcessor.java:194–225` | **OutboxProcessor-Recovery ist funktionslos**: `recoverSubmitToBlockchain()` ruft `txRepository.findById()` auf einer Verbindung auf, die `app.current_tenant=''` hat (TenantContext nicht gesetzt beim äußeren Transaction-Start). PostgreSQL-RLS filtert alle Zeilen → `Optional.empty()` → Recovery-Logik wird nie ausgeführt. Alle `FUNDS_HELD`/`SUBMITTED`-Transaktionen nach Systemabsturz werden nie recovert. | Verlorene Kundengelder: Funds debited, Blockchain-Transfer nie abgeschlossen, keine Recovery | BMAD Isolation: Outbox-Recovery muss TenantContext vor dem Query setzen | In `recoverSubmitToBlockchain()`: Tenant-ID per `adminJdbcTemplate` (BYPASSRLS) ermitteln, dann `TenantContext.set()` **vor** dem `getConnection()`-Call der neuen REQUIRES_NEW-Transaktion setzen. Alternativ: `adminJdbcTemplate` für den Guard-Read verwenden (analog `recoverInboundCompliance` Pattern) |
| **T-02** | Technik | `B2bTransferService.java:613–625` | **Crash-Fenster zwischen Ledger-Booking und DB-Persistierung**: `createLedgerBooking()` (Schritt 7, extern/irreversibel) läuft vor `persistLedgerRef()` (Schritt 8, REQUIRES_NEW). Absturz dazwischen → `bookingId` null → `transitionToFailed()` findet keine `ledgerBookingReference` → kein `reverseBooking()` → offene Kunden-Lastschrift ohne Stablecoin-Delivery. | Direkter Finanzverlust; keine automatische Erkennung; nur via manuellem Reconciliation detektierbar | Transaktionale Integrität: externe Calls müssen atomar mit DB-State sein | `persistLedgerRef()` in REQUIRES_NEW direkt nach `createLedgerBooking()` aufrufen; alternativ: Ledger-Booking-Referenz im Outbox-Pattern persistieren (Booking erst nach DB-Commit auslösen) |
| **C-01** | Compliance | `B2bTransferService.java:421–427`, `TenantSettings.java:88,91` | **EU Travel Rule nicht durchgesetzt (FATF Rec. 16 / Reg. 2023/1113)**: (a) `travel_rule_enabled` default `false` → opt-in statt mandatory; (b) Schwellwert 15.000 EUR statt €0 für self-hosted Wallets; (c) `originator_name` nie aus JWT/Kundenstamm gesetzt; (d) `travelRuleCompletedAt` bei Input-Validierung gesetzt, nicht bei VASP-Übertragung; (e) Inbound-Pfad sammelt gar keine TR-Daten. | Regulatorischer Verstoß gegen EU TFR; BaFin-Maßnahmen; Entziehung der Betriebserlaubnis möglich | API-First: Travel Rule muss in DTO-Validation und Service erzwungen werden | `travel_rule_enabled=true` hardwire für alle Tenants mit EU-Lizenz; Schwellwert auf €0 für non-custodial wallets; `originator_name` aus Kundenstammdaten automatisch befüllen; VASP-Transmission-Client (OpenVASP/TRISA) implementieren; `travelRuleCompletedAt` erst nach VASP-ACK setzen |
| **C-02** | Compliance | `AllUnityTokenAdapter.java:26–82` | **MiCA Art. 36 Deckungsprüfung nur in Mock-Adapter**: Die Deckungsreserven-Prüfung (`assertBaFinCoverage()`) existiert nur im Dev-Profil-Mock. Für prod-Profil gibt es keinen `HttpAllUnityClient`. In Produktion läuft kein Deckungscheck. | MiCA Art. 36 Verletzung: Aufsichtsbehördliche Untersagungsverfügung; potenzielle Haftung nach MiCA Art. 65 | Schichtenarchitektur: prod-Profil-Implementierung fehlt | `HttpAllUnityClient implements StablecoinTokenAdapter` mit `@Profile("prod")` erstellen; AllUnity Reserve API (`GET /v1/reserve/coverage`) vor jedem Transfer aufrufen; Coverage < 105 % → Transaktion ablehnen |

---

### PRIO MEDIUM — Kurzfristig beheben (innerhalb 30 Tage)

| ID | Kategorie | Komponente | Beschreibung | Empfohlene Lösung |
|----|-----------|------------|-------------|-------------------|
| **S-04** | Sicherheit | `RateLimitingFilter.java:137–143` | **`X-Forwarded-For` ohne Validierung**: IP-Rate-Limiting bypassbar durch beliebige Spoofing-Header. Webhook-Endpoint anfällig für DoS. | Nur letzten Hop aus `X-Forwarded-For` verwenden (hinter eigenem Reverse-Proxy); alternativ: Trusted-Proxy-Whitelist konfigurieren |
| **S-05** | Sicherheit | `application.yml:48` | **Hardcoded Default-Secret**: `phone-hmac-key: ${PHONE_HMAC_KEY:atruvia-stablecoin-2026}` — bekannter Fallback-Key ermöglicht Reverse-Engineering aller Telefonnummer-Hashes | Fallback-Default entfernen; Startup-Check: `if (phoneHmacKey.equals("atruvia-stablecoin-2026")) throw new IllegalStateException()` |
| **S-06** | Sicherheit | `JwtAuthFilter.java:64`, `DevAuthController.java:41` | **`getBytes()` ohne Charset-Angabe**: Plattform-abhängige Key-Bytes → nicht-portables JWT-Signing | `jwtSecret.getBytes(StandardCharsets.UTF_8)` in beiden Klassen |
| **S-07** | Sicherheit | V8 + alle Post-V8-Migrationen | **10 Tabellen ohne RLS**: `rate_quote`, `approval_workflow`, `outbox_message`, `phone_alias`, `institutional_address_book`, `tenant_settings`, `system_control`, `tax_event`, `reconciliation_run`, `limit_change_log` — cross-tenant Daten potenziell sichtbar über `stablecoin_app` | V21-Migration: RLS + Policy für `phone_alias`, `institutional_address_book`, `rate_quote`, `approval_workflow` (Tabellen mit sensiblen Kundendaten zuerst) |
| **S-08** | Sicherheit | `WebhookSignatureService.java:52–56` | **HMAC-Vergleich auf Hex-Strings statt Raw-Bytes**: `MessageDigest.isEqual` auf Hex-String nicht timing-safe | HMAC-Digests vor Hex-Encoding vergleichen: `MessageDigest.isEqual(computedDigest, receivedDigest)` auf `byte[]` |
| **S-09** | Sicherheit | `B2bController.java:189,208,252,271` | **Header-Injection via IBAN-Parameter**: `Content-Disposition: attachment; filename="camt053-{iban}.xml"` — IBAN nicht gegen `[\r\n"\\]` geprüft | IBAN-Sanitierung mit Regex `^[A-Z0-9]{15,34}$` vor Header-Verwendung |
| **S-10** | Sicherheit | `JwtAuthFilter.java:85–87` | **`MDC.remove("userId")` fehlt im finally-Block** | `MDC.remove("userId")` in finally-Block nach `MDC.remove("tenantId")` ergänzen |
| **F-05** | Zahlungsverkehr | `ComplianceService.java:34` | **Chainalysis-Request hardcoded `"USDC"/"POLYGON"`** für alle Währungen | `currency.name()` und `tenantSettings.getAllowedBlockchains()` übergeben |
| **F-06** | Zahlungsverkehr | `B2bTransferService.java:298–307` | **`reverseBooking()`-Fehler wird verschluckt**: Kein Re-throw, kein Outbox-Event, kein Alert | Outbox-Message `REVERSAL_FAILED` mit `bookingReference` + `holdId` schreiben; n8n-Alert triggern |
| **F-07** | Zahlungsverkehr | `KillSwitchFilter.java` | **Kill-Switch stoppt nicht OutboxProcessor**: Scheduled Jobs laufen trotz globalem Kill-Switch weiter | `KillSwitchService.isActive()` am Anfang von `processPendingMessages()` prüfen; bei aktivem Kill-Switch: Verarbeitung überspringen |
| **F-08** | Zahlungsverkehr | `InboundProcessingService.java:321–369` | **Unassigned Inbound ohne AML-Screening**: Gelder von sanktionierten Wallets auf Sammelkonto | `complianceService.screenAndAssert(senderWallet, ...)` vor Buchung auf Sammelkonto aufrufen |
| **F-09** | Zahlungsverkehr | `B2cYieldService.java:34–35` | **Zinsrate hardcoded**: `ANNUAL_YIELD_RATE = 3.5` in Code; kein Tenant-Override | `tenant_settings` um `yield_rate_annual` Spalte erweitern (V22-Migration); Rate aus TenantSettings lesen |
| **T-03** | Technik | `OutboxMessageRepository.java:15` | **Kein `SKIP LOCKED`**: Duplicate Processing in Cluster-Deployment | `@Query("SELECT m FROM OutboxMessage m WHERE m.status = 'PENDING' ORDER BY m.createdAt ASC")` mit `@Lock(PESSIMISTIC_WRITE)` + `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))` |
| **T-04** | Technik | `OutboxProcessor.java:75–104` | **Nicht-atomare Outbox-Status-Aktualisierung**: REQUIRES_NEW-Commit vor Status-Update | Status in Inner-REQUIRES_NEW-Transaktion setzen; alternativ: `PROCESSING`-Zwischenstatus einführen |
| **T-05** | Technik | `CommonController.java:56–102` | **BMAD-Verletzung**: Ownership-Check, CircleWalletClient-Aufruf, 4 Repository-Calls, Timeline-Building in Controller | `TransactionService.getTransactionForCustomer(customerId, txId)` extrahieren; `@Transactional(readOnly=true)` auf Service-Methode |
| **T-06** | Technik | `DvpEscrowService.java:115–173` | **DvP Escrow ohne Outbox/Recovery**: `settle()` und `cancel()` haben kein Crash-Recovery-Muster | Outbox-Events `DVP_SETTLE_INITIATED` und `DVP_CANCEL_INITIATED` vor externen Calls persistieren; äußere @Transactional entfernen; REQUIRES_NEW für DB-Writes |
| **T-07** | Technik | `B2bTransferService.java:462–488` | **Double-Approval-Race gibt HTTP 500**: `IllegalStateException` statt 409 Conflict | `@Transactional` + `@Version` (Optimistic Locking) auf `ApprovalWorkflow`; `IllegalStateException("APPROVED → APPROVED")` auf 409 in `GlobalExceptionHandler` mappen |
| **C-03** | Compliance | `ExportService.java` | **DATEV-Export ohne Zins/Kapital-Trennung**: Steuerrechtlich relevante Erträge nicht separat ausgewiesen | `YieldPosition.interestRate` und `YieldRedeem-Anteil` in Export-Record aufnehmen; `TaxEvent.grossYieldEur` im DATEV-Format (§45a EStG) |
| **C-04** | Compliance | `MockAtruviaTaxClient.java` | **FSA-Abzug stateless**: Pro Einlösung 1.000 EUR Freibetrag — kumulativ überschreitend möglich | `TaxEvent` mit `customerAccountId + taxYear` aggregieren; Jahresfreibetrag zentral tracken |
| **C-05** | Compliance | `ComplianceService.java` | **Kein Eskalationspfad bei Chainalysis-Ausfall**: Circuit-Breaker öffnet ohne Alert | n8n-Webhook bei `COMPLIANCE_FALLBACK_BLOCK` triggern; Quarantäne-Status für manuelle Review implementieren |

---

### PRIO LOW — Langfristig beheben (innerhalb 90 Tage)

| ID | Kategorie | Komponente | Beschreibung | Empfohlene Lösung |
|----|-----------|------------|-------------|-------------------|
| **S-11** | Sicherheit | `KillSwitchRequest.java`, `ReassignTransactionRequest.java` | Keine `@NotBlank`/`@NotNull`-Annotationen auf Admin-DTOs | Bean Validation Annotationen ergänzen |
| **S-12** | Sicherheit | `B2bController.java:249` | `LocalDateTime.parse(since)` ohne Error-Handling → 500 statt 400 | `@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)` verwenden |
| **S-13** | Sicherheit | `SecurityConfig.java:60` | `dev-token`-Pfad permanent `permitAll` unabhängig von devMode | URL aus permitAll entfernen; `@ConditionalOnProperty` am Controller reicht |
| **F-10** | Zahlungsverkehr | `RevenueService.java:28` | Legacy 2-Arg-Methode gibt B2C-Kunden B2B-Spread | Methode `@Deprecated` markieren und entfernen |
| **F-11** | Zahlungsverkehr | `B2bTransferService.java:207–210` | `accountRepository.findAll()` in `createRateQuote()` — Full-Table-Scan + falsches Account | `accountRepository.findByCustomerIdAndCustomerType(userId, B2B)` verwenden |
| **F-12** | Zahlungsverkehr | `B2bTransferService.java:611–613` | `max()`-Logik: Ledger-Credit ≠ P&L-Feld um `gasCostSimulated` | Einheitliche Revenue-Berechnung; `grossRevenue` = tatsächlicher Ledger-Credit-Anteil |
| **F-13** | Zahlungsverkehr | `TenantSettingsService.java:37–39` | Stille Default-Fallback ohne Alert bei nicht-konfiguriertem Tenant | `log.error` + n8n-Alert wenn `tenant_settings`-Zeile fehlt |
| **T-08** | Technik | `TenantAspect.java:22–27` | Nur Warning bei fehlendem TenantContext — keine Exception | In nicht-Scheduler-Kontexten `IllegalStateException` werfen (mit `@Profile`-Guard für Tests) |
| **T-09** | Technik | `application-dev.yml` | Kein `spring.task.scheduling.pool.size` → Single-Thread-Scheduler | `spring.task.scheduling.pool.size: 4` konfigurieren |
| **T-10** | Technik | `CommonController.java:82` | `tx.getCustomerAccount().getId()` ohne `@Transactional` → `LazyInitializationException`-Risiko | `@Transactional(readOnly=true)` auf `getTransaction()` Service-Methode |
| **C-06** | Compliance | `B2bTransferService.java:433` | `travelRuleCompletedAt` bei Input-Validation gesetzt, nicht bei VASP-Transmission | Timestamp nach VASP-Bestätigung setzen; `null` bis dahin |
| **C-07** | Compliance | Inbound-Pfad | Inbound-Transaktionen sammeln keine Travel Rule Originator-Daten | `senderKyc`-Felder im Inbound-Webhook-Request ergänzen; mit Address Book verknüpfen |

---

## 2. Konkrete Code-Gaps — Exakte Klassen, Zeilen und Methoden

### 2.1 Sicherheits-Schwachstellen

#### S-01: Dev-Mode Bypass — `JwtAuthFilter.java`
```java
// Zeilen 48–58 — KRITISCH: Entfernen oder IP-Whitelist + @Profile("dev") Guard
if (devMode && (authHeader == null || authHeader.isBlank())) {
    setAuthentication("dev-user");       // ← Vollzugriff ohne Token
    TenantContext.set("tenant-default"); // ← Beliebige Tenant-Kontext-Setzung
    try { chain.doFilter(request, response); }
    finally { TenantContext.clear(); }
    return;
}
```
**Fix:** Methode `doFilterInternal()` — Guard auf `@Profile("dev")` beschränken; in `SecurityConfig` stattdessen ein separates `devSecurityFilterChain` definieren.

#### S-02: Keine RBAC — `B2bController.java`
```java
// Zeile 287–320 — KRITISCH: Keine Rollenprüfung auf Admin-Endpunkten
@PostMapping("/admin/kill-switch/activate")
public ResponseEntity<String> activateKillSwitch(@RequestBody KillSwitchRequest request,
                                                  Authentication auth) {
    // ← Kein @PreAuthorize, kein hasRole() — jeder JWT-Inhaber kann dies aufrufen
    if ("GLOBAL".equalsIgnoreCase(request.scope())) {
        killSwitchService.activateGlobal(request.reason(), auth.getName());
```
**Fix:** `@PreAuthorize("hasRole('ATRUVIA_ADMIN')")` auf alle `/admin/**`-Methoden; Rolle `ATRUVIA_ADMIN` im JWT-`roles`-Claim; `SecurityConfig` um `requestMatchers("/api/v1/b2b/admin/**").hasRole("ATRUVIA_ADMIN")` erweitern.

#### S-03: IDOR — `B2bController.java` + `B2bTransferService.java`
```java
// B2bController.java Zeile 104–108 — KRITISCH: auth-Parameter deklariert aber ignoriert
@GetMapping("/transfers/{id}")
public ResponseEntity<TransactionResponse> getTransfer(@PathVariable UUID id,
                                                        Authentication auth) {
    return ResponseEntity.ok(transferService.getById(id)); // ← auth ignoriert!
}

// B2bTransferService.java Zeile 196–202 — Kein Ownership-Check
public TransactionResponse getById(UUID id) {
    StablecoinTransaction tx = txRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + id));
    // ← Kein tx.getCustomerAccount().getCustomerId().equals(requestingCustomerId)
```
**Fix:** Service-Signatur zu `getById(UUID id, String customerId)` ändern; Vergleich analog `CommonController.java:82`: `if (!tx.getCustomerAccount().getCustomerId().equals(customerId)) throw new AccessDeniedException(...)`.

#### S-09: Header-Injection — `B2bController.java`
```java
// Zeilen 189, 208, 252, 271 — MEDIUM: Unvalidierte IBAN in Content-Disposition
String filename = "camt053-" + resolvedIban + ".xml";
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
// ← resolvedIban kann \r\n oder " enthalten → Header-Injection
```
**Fix:**
```java
String safeIban = resolvedIban.replaceAll("[^A-Z0-9]", "");
```

---

### 2.2 Fachliche Gaps im Zahlungsverkehr

#### F-01: FX-Richtungsumkehr — `InboundProcessingService.java`
```java
// Zeile 213–214 — KRITISCH: × statt ÷
BigDecimal baseRate = fxRateService.getBaseRate(currency); // USDC: ≈ 1.0823 (EUR/USD)
BigDecimal amountEur = amountFiat.multiply(baseRate)       // FALSCH: 10000 × 1.0823 = 10823
    .setScale(6, RoundingMode.HALF_UP);
// KORREKT: amountFiat.divide(baseRate, 6, HALF_UP) = 9239 EUR
```
**Fix in `FxRateService.java`:** `getBaseRate(USDC)` semantisch: "Wie viel EUR kostet 1 USDC?" → ECB-Rate ist EUR/USD = 1.0823 → 1 USDC = 1/1.0823 EUR ≈ 0.9240 EUR. Beide Call-Sites (Inbound: multiply; Outbound: divide) müssen konsistent sein.

#### F-02/F-03: Limits nie durchgesetzt — `B2bTransferService.java`
```java
// Zeile 444–458 — KRITISCH: resolveSingleLimit() und resolveDailyLimit() nie aufgerufen
private StablecoinTransaction persistInitialTransaction(InitiateTransferRequest request,
                                                         CustomerAccount account,
                                                         String userId) {
    TenantSettings settings = tenantSettingsService.get(TenantContext.get());
    BigDecimal approvalThreshold = limitResolver.resolveApprovalThreshold(settings);
    // ← limitResolver.resolveSingleLimit(account, settings) FEHLT
    // ← limitResolver.resolveDailyLimit(account, settings) + Tages-Summe-Query FEHLT
    if (request.amountEur().compareTo(approvalThreshold) > 0) { ... }
```
**Fix:**
```java
BigDecimal singleLimit = limitResolver.resolveSingleLimit(account, settings);
if (request.amountEur().compareTo(singleLimit) > 0)
    throw new TaurusLimitExceededException("Einzeltransaktionslimit überschritten: " + singleLimit);
BigDecimal dailySum = txRepository.sumSettledAmountToday(account.getId(), LocalDate.now());
BigDecimal dailyLimit = limitResolver.resolveDailyLimit(account, settings);
if (dailySum.add(request.amountEur()).compareTo(dailyLimit) > 0)
    throw new TaurusLimitExceededException("Tageslimit überschritten: " + dailyLimit);
```

#### F-04: Kein FIU-Meldepfad — `ComplianceService.java`
```java
// Zeilen 53–64 — HIGH: AML-Block ohne GwG-§43-Meldung
} catch (ComplianceBlockException e) {
    AuditLog blockEntry = new AuditLog();
    blockEntry.setAction("AML_INBOUND_BLOCK");
    // ← Kein FIU-Client-Aufruf, kein Outbox-Event für SAR
    auditLogRepository.save(blockEntry);
}
```
**Fix:** `outboxRepository.save(new OutboxMessage("FIU_SAR_REQUIRED", payload))` nach AuditLog-Save; `OutboxProcessor` mit `FiuReportingService.submitSar()` ergänzen.

---

### 2.3 Technische & Architektonische Probleme

#### T-01: OutboxProcessor-Recovery-Totalausfall — `OutboxProcessor.java`
```java
// Zeile 194–225 — KRITISCH: TenantContext null → RLS filtert alle Zeilen
private void recoverSubmitToBlockchain(OutboxMessage msg, String txId) {
    StablecoinTransaction tx = txRepository.findById(UUID.fromString(txId))
            .orElse(null); // ← app.current_tenant='' → RLS → Optional.empty() → NULL
    if (tx == null) {
        log.warn("[OUTBOX] TX {} nicht gefunden ...", txId); // ← immer erreicht
        return; // ← Recovery nie ausgeführt
    }
```
**Fix:**
```java
// Tenant per adminJdbcTemplate (BYPASSRLS) ermitteln
String tenantId = adminJdbcTemplate.queryForObject(
    "SELECT tenant_id FROM stablecoin_transaction WHERE id = ?::uuid", String.class, txId);
if (tenantId == null) { log.warn(...); return; }
TenantContext.set(tenantId);
try {
    StablecoinTransaction tx = txRepository.findById(UUID.fromString(txId)).orElse(null);
    if (tx == null) { log.warn(...); return; }
    // ... Recovery-Logik
} finally { TenantContext.clear(); }
```

#### T-02: Crash-Fenster Ledger-Booking — `B2bTransferService.java`
```java
// Zeilen 613–625 — KRITISCH: Externe Operation vor DB-Commit
// Schritt 7: Ledger-Booking (extern, irreversibel)
BookingResponseDto booking = coreBankingClient.createLedgerBooking(new LedgerBookingDto(...));
// ← Absturz hier: bookingId verloren, keine Reversal-Möglichkeit
// Schritt 8: DB-Persistierung (REQUIRES_NEW, committed)
self.persistLedgerRef(txId, booking.bookingId());
// Schritt 9: Status SETTLED (REQUIRES_NEW)
self.settleTransaction(txId, adapterResult.blockchainHash(), revenue);
```
**Fix (Outbox-Ansatz):**
```java
// Outbox-Message VOR dem Ledger-Booking schreiben (REQUIRES_NEW)
self.persistLedgerBookingIntent(txId, intentPayload);
// Dann extern buchen
BookingResponseDto booking = coreBankingClient.createLedgerBooking(...);
// bookingId sicher persistieren
self.persistLedgerRef(txId, booking.bookingId());
self.settleTransaction(txId, ...);
```

#### T-05: BMAD-Verletzung CommonController — `CommonController.java`
```java
// Zeilen 52–102 — MEDIUM: Business-Logik und Repository-Calls im Controller
@GetMapping("/accounts/{iban}/balance")
public ResponseEntity<AccountBalanceResponse> getBalance(@PathVariable String iban,
                                                          Authentication auth) {
    // ← Ownership-Check in Controller (Zeile 56–59)
    CustomerAccount requestingAccount = accountRepository.findByCustomerId(auth.getName())...;
    if (!requestingAccount.getIban().equals(iban)) throw new AccessDeniedException(...);
    // ← CircleWalletClient-Aufruf in Controller (Zeile 65)
    CircleWalletBalanceDto walletBalance = circleWalletClient.getWalletBalance("BANK_MASTER_WALLET_ID");
    // ← Aggregations-Logik in Controller (Zeilen 66–68)
```
**Fix:** `AccountService.getBalanceForCustomer(iban, customerId)` Service-Methode; Ownership-Check + Wallet-Abfrage + Aggregation dorthin verschieben; Controller ruft nur Service auf.

---

### 2.4 Regulatorische & Compliance-Gaps

#### C-01: Travel Rule — mehrere Klassen
```java
// InitiateTransferRequest.java Zeilen 18–23 — KRITISCH: Kein @NotBlank auf TR-Feldern
String beneficiaryName;      // ← @NotBlank fehlt
String beneficiaryAddress;   // ← @NotBlank fehlt
String beneficiaryAccountId; // ← @NotBlank fehlt

// B2bTransferService.java Zeile 421–427 — travel_rule_enabled als opt-in
TenantSettings settings = tenantSettingsService.get(TenantContext.get());
if (settings.isTravelRuleEnabled()           // ← opt-in, Default false
    && request.amountEur().compareTo(settings.getTravelRuleThresholdEur()) > 0) {

// B2bTransferService.java Zeile 433 — Timestamp falsch gesetzt
tx.setTravelRuleCompletedAt(LocalDateTime.now()); // ← bei Input-Validation, nicht nach VASP

// TenantSettings.java Zeile 91 — Schwellwert 15.000 EUR (EU TFR: €0 für self-hosted)
private BigDecimal travelRuleThresholdEur = new BigDecimal("15000.000000");
```
**Fix:**
1. `travel_rule_enabled` → nicht mehr als Feature-Flag; immer `true` für EU-lizenzierte Tenants
2. `@NotBlank` auf alle TR-Felder wenn Betrag > Schwellwert → Bean Validation Group verwenden
3. Schwellwert auf `0.00` für non-custodial wallets (V23-Migration: `self_hosted_wallet_threshold_eur DECIMAL DEFAULT 0`)
4. `travelRuleCompletedAt` erst nach VASP-ACK setzen

#### C-02: GwG §43 — Kein FIU-Meldepfad
Kein einziger Java-Source enthält `FIU`, `FinancialIntelligenceUnit`, `Verdachtsmeldung`, `SAR` als Code-Konstrukt (nur Kommentare).

**Fix:** `FiuOnlineClient` Interface + Mock (dev) + Http-Impl (prod) mit `submitSuspiciousActivityReport(SarDto)`. `SarDto` enthält: transactionId, walletAddress, amount, currency, suspicionCode (AML_HIT, SANCTIONS_HIT), timestamp.

---

## 3. Zusammenfassende Risikomatrix

| Prio | Anzahl | Finanzrisiko | Regulierungsrisiko |
|------|--------|-------------|-------------------|
| HIGH (Sofort) | 11 | Direkte Verluste durch FX-Fehler, ungesicherte Limits, verlorene Buchungen | GwG-Bußgeld (bis 5 Mio. EUR), MiCA-Untersagung, EU TFR-Verstoß |
| MEDIUM (30 Tage) | 14 | Reputationsrisiko, DSGVO-Verletzungen, Betriebsunterbrechungen | BaFin MaRisk-Beanstandung, BAIT-Audit-Findings |
| LOW (90 Tage) | 12 | Technische Schulden, Test-Coverage-Lücken | Hinweise im BAIT-Audit |
| **Gesamt** | **37** | | |

---

## 4. Sofortmaßnahmen (Priorität 1 — nächste 5 Werktage)

1. **`InboundProcessingService.java:214`** — `multiply(baseRate)` → `divide(baseRate, 6, HALF_UP)` [F-01] → direkter Finanzverlust stoppt sofort
2. **`B2bTransferService.java:444`** — `limitResolver.resolveSingleLimit()` + `resolveDailyLimit()` aufrufen [F-02/F-03] → Limit-Enforcement aktivieren
3. **`B2bController.java:287–320`** — `@PreAuthorize("hasRole('ATRUVIA_ADMIN')")` auf alle Admin-Endpoints [S-02]
4. **`B2bController.java:107`** — Ownership-Check ergänzen [S-03]
5. **`OutboxProcessor.java:194`** — TenantContext vor `findById` setzen [T-01] → Recovery-Logik wird erstmals funktionieren
6. **Produktions-Review**: Prüfen ob PROD mit `SPRING_PROFILES_ACTIVE=dev` deployed — wenn ja: sofortiger Rollback

---

*Generiert: 2026-08-20 | Auditor: Lead IT-Auditor & Principal Security & Compliance Engineer, Atruvia AG | 37 Findings (11 HIGH, 14 MEDIUM, 12 LOW)*
