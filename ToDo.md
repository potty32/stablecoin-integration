# ToDo — Atruvia Stablecoin Integration Platform

> **Stand:** 2026-08-20 | **Grundlage:** Repository-Audit + Dev-Portal-Vollständigkeitsprüfung  
> **Erstellt durch:** Principal Technical Writers & DevEx Architects, Atruvia AG  
> **Teststand:** 238 Tests | 238 bestanden | Flyway V1–V24

---

## Kategorie 1 — Security & Compliance

> Alle Findings aus dem AUDIT_SECURITY_TECHNICAL.md (2026-08-20), priorisiert nach Dringlichkeit.

| ID | Prio | Befund | Lösung | Status |
|----|------|--------|--------|--------|
| **S-01** | 🔴 HIGH | Dev-Mode-Bypass: `devMode=true` ohne IP-Whitelist lässt jeden Request ohne Token durch | IP-Whitelist auf 127.0.0.1 / Build-Guard mit `@Profile("dev")` trennen | Backlog |
| **S-07-ext** | 🔴 HIGH | RLS fehlt noch auf `outbox_message` — enthält TX-Payload cross-tenant sichtbar | V25: `outbox_message.tenant_id` + RLS (oder klar als System-Tabelle deklarieren) | Backlog |
| **C-01** | 🔴 HIGH | **EU TFR Travel Rule**: `originator_name` nie aus Kundenstamm befüllt; Threshold 15k EUR statt €0 für Self-Hosted Wallets; kein VASP-zu-VASP-Protokoll (OpenVASP/TRISA) | Pflichtfelder aus Kundenstamm füllen; TRISA-Anbindung; `travelRuleCompletedAt` erst nach VASP-ACK | Backlog Phase 3 |
| **C-02** | 🔴 HIGH | **MiCA Art. 36**: AllUnity-Deckungsprüfung nur im Dev-Mock — kein `HttpAllUnityClient` für prod | `HttpAllUnityClient implements StablecoinTokenAdapter @Profile("prod")` | Backlog Phase 2 |
| **F-04** | 🔴 HIGH | **GwG §43 FIU-Meldepflicht**: Kein automatisierter FIU-Online-Meldeweg bei `AML_INBOUND_BLOCK` (nur `audit_log`) | `FiuOnlineClient` + Outbox-Event `FIU_SAR_REQUIRED`; Bußgeld bis 5 Mio. EUR | Backlog Phase 2 |
| **S-10** | 🟡 MED | `MDC.remove("userId")` fehlt im finally-Block von `JwtAuthFilter` — userId-Leak in Logs | `MDC.remove("userId")` nach `MDC.remove("tenantId")` ergänzen | Quick Fix |
| **S-11** | 🟡 MED | `KillSwitchRequest` + `ReassignTransactionRequest` ohne `@NotBlank`/`@NotNull` | Bean Validation Annotationen ergänzen | Quick Fix |
| **S-12** | 🟡 MED | `LocalDateTime.parse(since)` in Export-Endpoints ohne Error-Handling → 500 statt 400 | `@DateTimeFormat(iso=ISO.DATE_TIME)` auf `@RequestParam` | Quick Fix |
| **C-05** | 🟡 MED | Kein Eskalationspfad bei Chainalysis-Ausfall (Circuit-Breaker öffnet ohne Alert + kein Quarantäne-Status) | n8n-Webhook bei `COMPLIANCE_FALLBACK_BLOCK`; Quarantäne-Status für manuelle Review | Backlog Phase 2 |
| **T-06** | 🟡 MED | DvP Escrow ohne Outbox/Recovery: `settle()` und `cancel()` bei DB-Fehler → Doppel-Settlement | Outbox-Events `DVP_SETTLE_INITIATED` + `DVP_CANCEL_INITIATED` vor externen Calls | Backlog Phase 2 |
| **WEBHOOK-SEC** | 🟡 MED | `X-Circle-Signature` HMAC-Verifikation: Im dev-Profil deaktiviert — Prod-Freigabe riskant ohne expliziten Guard | Startup-Check: in prod MUSS Circle-Secret gesetzt sein; HMAC-Prüfung niemals deaktivierbar | Vor Go-Live |
| **CIRCLE-SECRET** | 🔴 HIGH | `CIRCLE_WEBHOOK_SECRET` muss in Prod gesetzt werden (Env-Variable, kein Default) | Dokumentation + Deployment-Checkliste | Vor Go-Live |

---

## Kategorie 2 — Technische Schulden & Testabdeckung

| ID | Prio | Befund | Lösung | Status |
|----|------|--------|--------|--------|
| **T-02-ext** | 🟡 MED | Ledger-Booking-Crash-Fenster: `LEDGER_BOOKING_INTENT` Outbox-Intent noch nicht für OutboxProcessor verarbeitet | `recoverLedgerBookingIntent()` in OutboxProcessor implementieren | Backlog |
| **T-COVERAGE** | 🟡 MED | Jacoco-Coverage: LINE ~82%, BRANCH ~71% — Ziel 80% Branch-Coverage | WireMock Contract-Tests für `client/http`; Edge-Cases in B2bStateMachineTest | Sprint |
| **T-WIREMOCK** | 🟡 MED | Keine WireMock Contract-Tests für `HttpCircleWalletClient`, `HttpEcbRateClient`, `HttpChainalysisClient` | WireMock-Stubs für alle drei HTTP-Clients | Sprint |
| **T-SCHEDULER** | 🔵 LOW | `spring.task.scheduling.pool.size` nicht konfiguriert → Single-Thread-Scheduler | `spring.task.scheduling.pool.size: 4` in application-dev.yml | Quick Fix |
| **T-POOL** | 🔵 LOW | `adminDataSource` ohne expliziten Connection-Pool-Size-Wert → HikariCP default 10 | `initializationFailTimeout: -1` (lazy) + `maximumPoolSize: 5` konfigurieren | Quick Fix |
| **HTTPDZBANKHEDGE** | 🟡 MED | `HttpDzBankHedgeClient` nur als Interface + Mock — fehlt prod-Implementierung (MiCA Art. 45) | HTTP-Client gegen DZ-Bank MiCA Hedge-API implementieren | Backlog Phase 2 |
| **HTTPQIVALIS** | 🟡 MED | `QivalisTokenAdapter` nur dev-Mock — kein `HttpQivalisClient @Profile("prod")` | HTTP-Client gegen Qivalis Consortium Settlement Layer (Layer-2 Ethereum) | Backlog Phase 2 |
| **ALLUNITY-PROD** | 🔴 HIGH | `AllUnityTokenAdapter` nur dev-Mock — kein `HttpAllUnityClient @Profile("prod")` | Siehe C-02 oben | Backlog Phase 2 |
| **B2B-CONTROLLER-TESTS** | 🟡 MED | `B2bControllerTest` mockt alle Services — kein echter Controller-Integrationstest für DvP-Endpoints | DvP-Endpoints in Controller-Integrationstests abdecken | Sprint |
| **ANGULAR-TESTS** | 🔵 LOW | Angular Production Build nicht verifiziert; kein `ng test` in CI | `ng build --configuration production` + `ng test` in CI-Pipeline | Backlog |

---

## Kategorie 3 — Fachliche & Regulatorische Lücken

| ID | Prio | Befund | Lösung | Status |
|----|------|--------|--------|--------|
| **F-09** | 🟡 MED | Yield-Rate hardcoded 3,5% p.a. — kein Tenant-Override möglich | V25: `yield_rate_annual DECIMAL(5,4)` in `tenant_settings`; `B2cYieldService` aus DB lesen | Backlog Phase 2 |
| **F-REMITTANCE** | 🟡 MED | **UC-13 Remittance: `recipientPhone`-Feld fachlich falsch** — Mexiko (CLABE), Nigeria (NUBAN), Philippinen (GCash) kennen keine IBANs und keine einheitliche Kontonummer. Aktuell wird das Feld im Backend ignoriert; Empfänger-Routing läuft immer über eine hardcodierte Gateway-Wallet. Empfänger-Identifikation muss länderspezifisch sein (CLABE 18-stellig für MX, NUBAN 10-stellig für NG, GCash-Mobilnummer für PH). `RemittanceRequest.recipientPhone` → `recipientAccount` mit dynamischem Label + Validierung je Land. Optional: echter Remittance-Provider-Client (Bitso/MX, BitPesa/NG). | Fachliche Analyse abgeschlossen (2026-08-20) — Umsetzung nach Prioritätsentscheidung | Backlog |
| **C-03** | 🟡 MED | DATEV-Export: Zins und Kapital nicht getrennt ausgewiesen (EStG §45a) | `TaxEvent.grossYieldEur` im DATEV-Format; Zins-Kapital-Trennung im `ExportService` | Backlog Phase 3 |
| **C-04** | 🟡 MED | FSA (Freistellungsauftrag) Abzug stateless pro Einlösung statt kumulativ pro Jahr | `TaxEvent` aggregieren per `customerAccountId + taxYear`; Jahresfreibetrag zentral tracken | Backlog Phase 3 |
| **C-06** | 🔵 LOW | `travelRuleCompletedAt` bei Input-Validierung gesetzt — muss nach VASP-Bestätigung erfolgen | Timestamp erst nach VASP-ACK; `null` bis dahin | Backlog Phase 3 |
| **F-INBOUND-SPREAD** | 🔵 LOW | Inbound FX-Spread nicht berechnet — Inbound-Transaktionen immer `grossRevenue=0` | TenantSettings `fxSpreadB2b/B2c` auch auf Inbound-Pfad anwenden | Backlog |
| **F-RATE-QUOTE** | 🟡 MED | `createRateQuote()` findet erstes B2B-Konto via `findAll()` (Full-Table-Scan) — falsches Konto bei Multi-Account-Tenant | `findByCustomerIdAndCustomerType(userId, B2B)` statt `findAll().stream().filter()` | Quick Fix |
| **N8N-ALERTS** | 🟡 MED | n8n-Alert-Kanal für `ReconciliationService`, `OutboxMonitorService` noch nicht vollständig verdrahtet | n8n-Webhook-URL für alle Compliance-Events konfigurieren; Alert-Kanal testen | Backlog |
| **MARKTBANK-TENANT** | 🟡 MED | `tenant-marktbank` hat keine Customer-Accounts in V24-Seed — Dev-Portal für Marktbank komplett nicht nutzbar | V25 oder separates Seed-Script für Marktbank-Accounts | Quick Fix |

---

## Kategorie 4 — Dev-Portal & UI/UX Gaps

> Ergebnis der Vollständigkeitsprüfung (2026-08-20). Von 36 dokumentierten UCs + 15 G-xx-Fixes:
> ✅ Vollständig interaktiv: ~13 UCs | ⚠️ Partiell: ~6 UCs | ❌ Absent: 17 UCs + 11 G-xx-Fixes

### 4.1 Kritische Gaps (Prio HIGH)

| ID | Befund | Lösung |
|----|--------|--------|
| **UI-DvP** | DvP Engine (UC-33/34/35) komplett absent — kein Lock/Settle/Cancel-Widget | Neuer Tab "DvP & Wertpapiere" mit drei Aktionskarten + Inline-Formular |
| **UI-MARKTBANK** | `tenant-marktbank` wählbar aber komplett nicht nutzbar (0 User) | V25-Seed für Marktbank + 2 User im Portal (`cust-b2b-marktbank`, `cust-b2c-marktbank`) |
| **UI-KILLSWITCH** | G-07-Karte vorhanden aber Buttons nicht verdrahtet — `activateKillSwitch()` / `deactivateKillSwitch()` nicht aufgerufen | Direkte Aktionsbuttons "🔴 Aktivieren" / "🟢 Deaktivieren" in der Karte verdrahten |
| **UI-MULTITOKEN** | UC-32 (EURAU/EURQ Token) invisible — kein Einstiegspunkt für neue Token-Typen im Portal | Währungs-Selektor im Transfer-Formular erweitern; EURAU/EURQ als Optionen; Adapter-Badge anzeigen |
| **UC-ID-MISMATCH** | 10 UC-Cards haben falsche IDs (v1-Nummerierung statt v2) — Auditor-Verwechslungsgefahr | UC-Labels in Dev-Portal auf USE_CASES_v2.md-Nummerierung angleichen |

### 4.2 Mittlere Gaps (Prio MEDIUM)

| ID | Befund | Lösung |
|----|--------|--------|
| **UI-INST-ADDRESSBOOK** | UC-24/25/26 (Inst. Adressbuch: Add/List/Revoke) komplett absent | Neue Karten im Tab "Compliance & Admin" oder neuer Tab "Interbanken" |
| **UI-PHONE-REGISTER** | UC-15 (Telefon-Alias Registration) fehlt — P2P-Card setzt Alias voraus | Vorgeschalteter "Alias registrieren"-Schritt in der P2P-Karte als Accordion |
| **UI-RETOURE-ACCOUNT-SUSPEND** | UC-30 (Automatische Retoure) nur partiell — Konto auf SUSPENDED setzen nicht möglich | Admin-API-Call "Konto sperren" als Aktion in der Compliance-Karte |
| **UI-REASSIGN** | UC-31 (Sammelkonto) Reassign-Schritt fehlt — POST `/admin/reassign-transaction` nicht verdrahtet | "Zuordnen"-Aktion in der Sammelkonto-Karte nach Webhook-Sim |
| **UI-BALANCE** | UC-21 (Kontostand) fehlt als eigenständige Karte | Widget in der Nutzer-Login-Sektion des Dev-Portals (wird bei Login bereits abgerufen) |
| **UI-GROSSE-VB-APPROVER** | tenant-grosse-vb hat keinen Zweitfreigeber-Account → Vier-Augen-Test für Große VB nicht möglich | V25: `cust-b2b-approver` für tenant-grosse-vb hinzufügen |

### 4.3 Niedrige Gaps (Prio LOW)

| ID | Befund | Lösung |
|----|--------|--------|
| **UI-CARD-WALLET** | UC-19 (Card-Wallet) komplett absent | Informationskarte in B2C-Tab |
| **UI-MICROPAYMENT** | UC-20 (Biometrie-Micropayment) komplett absent | Karte mit Formular für Merchant-ID + Betrag |
| **UI-YIELD-VIEW** | UC-18 (Yield-Position abrufen) fehlt als Read-Karte | Inline-Anzeige im Yield-Tab nach Deposit |
| **UI-G02-TAX** | G-02 (Kapitalertragsteuer/TaxClient) keine Karte | Info-Karte im BaFin-Tab mit TaxEvent-Übersicht |
| **UI-G11-OUTBOX** | G-11 (OutboxMonitor) keine Karte — kritisch für Compliance | Monitor-Status-Widget in Compliance-Tab |
| **UI-G15-YEAR-END** | G-15 (Yield-Jahresabschluss) keine Karte — regulatorisch relevant | Karte mit manueller Trigger-Option + Status |
| **UI-CAMT029-LABEL** | UC-29b falsch gelabelt (sollte G-10 sein) | Label korrigieren |
| **UI-G04-RECONCILE** | G-04 (EOD Reconciliation) Karte ohne Trigger-Button | "Reconciliation jetzt starten"-Button verdrahten |
| **UI-MISSINGCOUNT** | `missingUiCount = 11` hardcoded und stark veraltet (tatsächlich ~19 UCs absent) | Wert aus `tabs`-Array berechnen oder entfernen |

---

## Entwicklungsfahrplan

### Phase 1 — Go-Live Hardening & Security (Prio: SOFORT, 2–4 Wochen)

> Voraussetzung für produktiven Bankbetrieb. Keine dieser Punkte darf in Produktion fehlen.

| # | Maßnahme | Prio | Aufwand |
|---|----------|------|---------|
| 1.1 | `PHONE_HMAC_KEY` + `CIRCLE_WEBHOOK_SECRET` in Prod-Deployment erzwingen | 🔴 | 1h |
| 1.2 | `HttpAllUnityClient` für prod-Profil (MiCA Art. 36 Deckungsprüfung real) | 🔴 | 3 Wochen |
| 1.3 | FIU-Online-Meldepfad (GwG §43) — `FiuOnlineClient` + Outbox | 🔴 | 3 Wochen + Legal |
| 1.4 | EU TFR Travel Rule: Originator-Daten aus Kundenstamm; €0-Threshold für Self-Hosted | 🔴 | 4 Wochen + Legal |
| 1.5 | Quick Fixes: S-10 (MDC userId), S-11 (DTO Validation), S-12 (DateTimeFormat) | 🟡 | 2h |
| 1.6 | `tenant-marktbank` Seed-Accounts (V25) + Portal-User | 🟡 | 30 Min |
| 1.7 | Dev-Portal UC-ID-Korrektur (v1→v2 Nummerierung) | 🟡 | 2h |
| 1.8 | Kill-Switch-Buttons im Dev-Portal verdrahten (G-07) | 🟡 | 1h |
| 1.9 | Jacoco Branch-Coverage auf 80% heben (WireMock Contract-Tests) | 🟡 | 1 Woche |

### Phase 2 — Qivalis-Konsortial-Erweiterung & Regulatorik (4–8 Wochen)

> Multi-Issuer-Plattform vervollständigen und regulatorische Lücken schließen.

| # | Maßnahme | Prio | Aufwand |
|---|----------|------|---------|
| 2.1 | `HttpQivalisClient` für prod-Profil (DZ Bank Consortium Settlement Layer) | 🔴 | 4 Wochen |
| 2.2 | `HttpDzBankHedgeClient` implementieren (MiCA Art. 45 FX-Absicherung) | 🔴 | 2 Wochen |
| 2.3 | FIU-Meldepfad vollständig operativ (inkl. FIU-Online API-Test) | 🔴 | 2 Wochen |
| 2.4 | DvP-Engine im Dev-Portal: Tab "DvP & Wertpapiere" mit UC-33/34/35 | 🟡 | 1 Woche |
| 2.5 | Multi-Token UC-32 im Dev-Portal: EURAU/EURQ Selektor | 🟡 | 3 Tage |
| 2.6 | Yield-Rate pro Tenant (V25: `yield_rate_annual` in `tenant_settings`) | 🟡 | 3 Tage |
| 2.7 | Chainalysis-Ausfall-Eskalation: n8n-Alert + Quarantäne-Status | 🟡 | 1 Woche |
| 2.8 | DvP Escrow Recovery (Outbox-Pattern für settle/cancel) | 🟡 | 1 Woche |
| 2.9 | Grosse-VB Approver + Inst. Adressbuch im Dev-Portal | 🔵 | 2 Tage |

### Phase 3 — agree21-Cloud-Anbindung via Kafka & S3 (8–16 Wochen)

> Vollständige Cloud-Native-Architektur für die Atruvia-Produktionsumgebung.

| # | Maßnahme | Prio | Aufwand |
|---|----------|------|---------|
| 3.1 | agree21 Core-Banking-Anbindung via Kafka (statt Mock-CoreBankingClient) | 🔴 | 8 Wochen |
| 3.2 | Produktive Kafka-Cluster-Anbindung (statt DevKafkaProducer/-Consumer) | 🔴 | 4 Wochen |
| 3.3 | S3-Export-Anbindung (statt `DevExportStorageService`) | 🟡 | 2 Wochen |
| 3.4 | OpenVASP/TRISA Travel-Rule-Transmission (VASP-zu-VASP) | 🔴 | 6 Wochen + TRISA-Mitgliedschaft |
| 3.5 | DATEV-Export: Zins/Kapital-Trennung (EStG §45a), FSA kumulativ | 🟡 | 2 Wochen + Steuerberater |
| 3.6 | CAMT.054 Echtzeit-Avisierung: Presigned-S3-URL-Flow statt Sync-Download | 🟡 | 1 Woche |
| 3.7 | Angular Production Build + CI-Pipeline (GitHub Actions / Jenkins) | 🟡 | 1 Woche |
| 3.8 | OpenTelemetry Tracing: Jaeger/Grafana-Anbindung in Prod | 🔵 | 2 Wochen |
| 3.9 | PSD2 SCA (Strong Customer Authentication) Integration | 🔴 | 6 Wochen + Legal |
| 3.10 | Multi-Region-Deployment (DORA Art. 17 Business Continuity) | 🔴 | 8 Wochen |

---

## Schnell-Referenz: Nächste konkrete Schritte (ab morgen früh)

```bash
# 1. V25 Migration erstellen (Marktbank-Seed + Grosse-VB-Approver + Yield-Rate-Spalte)
# 2. UC-IDs in dev-portal.component.ts korrigieren (v1→v2)
# 3. Kill-Switch-Buttons in dev-portal verdrahten
# 4. MDC.remove("userId") in JwtAuthFilter.java ergänzen  
# 5. WireMock Contract-Tests für HttpCircleWalletClient
# 6. AllUnity HttpClient Skeleton @Profile("prod") anlegen
```

---

*Erstellt: 2026-08-20 | Audit-Grundlage: 238 Tests · Flyway V1–V24 · 36+ UC · 15 G-Fixes*  
*Letzte Aktualisierung: bei jedem Sprint-Abschluss in `HANDOVER.md` und `QA_REVIEW_CHANGES.md` mitpflegen*
