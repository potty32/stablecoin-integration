# QA Review — Änderungsdokumentation
## Atruvia Stablecoin Integration Platform — Sprint-Abschluss 2026-08-18

> **Klassifizierung:** INTERN · Für Code-Review und BaFin-Auditierung  
> **Reviewer:** Principal Core Banking Architect & BaFin Lead Compliance Auditor  
> **Commit-Range:** `68bb995` → `f31336e` (8 Commits, 1 Tag)  
> **Tests:** 125 | 0 Failures | 0 Errors  
> **Flyway:** V1–V18 (9 neue Migrationen heute)

---

## 1. Executive Summary

Dieser Sprint erweitert die Atruvia Stablecoin Integration Platform um drei Hauptbereiche:

1. **Sicherheitsschicht** — Webhook-Signaturvalidierung (HMAC-SHA256), OutboxProcessor RLS-Fix, Telefonnummer-Hash-Härtung
2. **Enterprise Payment Features** — CAMT.054 Echtzeit-Avisierung, automatische Retouren (R-Transaktionen), Sammelkonto-Prinzip
3. **BaFin-Gap-Schließung** — 15 Compliance-Lücken (G-01 bis G-15) aus dem Functional Audit behoben

---

## 2. Commit-Übersicht

| Commit | Zeitpunkt | Beschreibung |
|---|---|---|
| `68bb995` | 01:28 | Multi-Tenancy via PostgreSQL RLS (V8) |
| `a607b2e` | 02:19 | Inbound Stablecoin Processing (UC-27, V9) |
| `1f06eba` | 02:47 | Multi-Tenancy + Inbound + Return-Logik (konsolidiert) |
| `1cd16f5` | 07:54 | Webhook-Signatur (AUTH_002), OutboxProcessor RLS-Fix, settledAt |
| `87989d9` | 08:58 | Enterprise Payment Features (UC-29/30/31, V10) |
| `a3292e8` | 10:55 | BaFin Gaps G-01 bis G-07 (V11–V15) |
| `f31336e` | 12:15 | BaFin Gaps G-08 bis G-15 (V16–V18) |

---

## 3. Neue Features & Use Cases

### UC-27 · Inbound Stablecoin Empfang (Webhook)

**Flow:** `POST /api/v1/b2b/inbound/webhook` → Cross-Tenant-Wallet-Lookup (adminJdbcTemplate) → Idempotenz-Check (blockchainHash) → `CREATED → INCOMING` + Outbox-Recovery → AML-Screening (Chainalysis) → `COMPLIANCE_PENDING → COMPLIANCE_APPROVED → SETTLED` + CoreBanking-Gutschrift

**Sicherheit:** HMAC-SHA256-Signaturprüfung (`X-Circle-Signature`-Header)

**Neue Dateien:**
- `controller/InboundWebhookController.java`
- `service/inbound/InboundProcessingService.java`
- `service/compliance/WebhookSignatureService.java`
- `client/mock/MockAtruviaTaxClient.java` (G-02)

---

### UC-28 · Multi-Tenancy via PostgreSQL RLS

**Architektur:**
```
JWT {tenant: "tenant-kleine-vb"}
  └─ JwtAuthFilter → TenantContext.set()
  └─ TenantAwareDataSource → set_config('app.current_tenant', ...)
  └─ PostgreSQL RLS-Policy: USING (tenant_id = current_setting('app.current_tenant', true))
```

**RLS-Tabellen:** `customer_account`, `stablecoin_transaction`, `address_book`, `yield_position`, `audit_log`  
**Tenant-freie Tabellen:** `institutional_address_book`, `approval_workflow`, `outbox_message`, `system_control`

---

### UC-29 · CAMT.054 Echtzeit-Avisierung

- `ExportService.generateCamt054(iban)` — ISO 20022 CAMT.054.001.08
- Filter: `type=INBOUND`, `status=SETTLED`
- `CdtDbtInd=CRDT`, BkTxCd: `PMNT/RCDT/ESCT`
- Endpunkt: `GET /api/v1/b2b/export/camt054?iban=...`

---

### UC-30 · Automatische Retouren

**Trigger:** Konto-Status `SUSPENDED`/`BLOCKED` nach erfolgreichem AML-Screening.

**State-Flow:**
```
Original INBOUND:  COMPLIANCE_APPROVED → FAILED (reason: KONTO_INAKTIV)
Return INBOUND_RETURN: CREATED → RETURNED (direkt, da REQUIRES_NEW-Sichtbarkeitsregel)
```

**Neue Felder:** `parent_transaction_id`, `TransactionType.INBOUND_RETURN`, `TransactionStatus.RETURNED`

---

### UC-31 · Sammelkonto-Prinzip

**Trigger:** Unbekannte Wallet-Adresse (kein matching `customer_account.wallet_address`)

**Flow:**
1. `processUnassignedInbound()` → TX auf `customer_id='unassigned-funds'` mit `status=UNASSIGNED`
2. Admin: `POST /api/v1/b2b/admin/reassign-transaction {transactionId, targetIban}`
3. `ReassignTransactionService.reassign()` → cross-tenant Update + CoreBanking-Buchung → `SETTLED`

---

## 4. BaFin Gap-Behebungen

### G-01 · Buchungskreislauf (MiCA Art. 23, HGB §246) — KRITISCH

| Vorher | Nachher |
|---|---|
| Hold = `amountFiat` | Hold = `grossDebit` (+ Fee + Spread) |
| Recipient erhält weniger als Sender sendet | Recipient erhält vollen `amountFiat`, Bank bucht Fee+Spread |
| Kein Storno bei Circle-Failure nach Ledger | `reverseBooking()` bei `ledger_booking_reference != null` |

**Neue Spalten (V11):** `gross_debit`, `fee_amount`, `ledger_booking_reference`, `slippage_tolerance_bps`, `tax_withheld`

---

### G-02 · Kapitalertragsteuer — Drittsystem (EStG §43/§44) — KRITISCH

- `AtruviaTaxClient` Interface → Atruvia Tax Engine (Drittsystem)
- `MockAtruviaTaxClient`: FSA 1.000 EUR/Jahr, `FSA_COVERED`/`PARTIAL_FSA`/`TAX_APPLIED`
- `B2cYieldService.redeem()`: Netto-Buchung + `TaxEvent` Audit-Nachweis (V13)
- Kein eigenes FSA/KiSt-Management in dieser Plattform

---

### G-03 · Mandantenspezifische Konfiguration — KRITISCH

- `TenantSettings` Entity (V12): FX-Spread, Fees, Limits, Travel-Rule, Bulk-Quote, Kill-Switch
- Seed-Daten für alle 4 Dev-Tenants
- `B2bTransferService`: alle preisrelevanten Parameter aus TenantSettings

---

### G-04 · Reconciliation (AT 7.2 MaRisk) — KRITISCH

- `ReconciliationService` @Scheduled(23:00), pro Tenant
- `ReconciliationRun` Entity (V14)
- Status: BALANCED | DISCREPANCY (>1 Cent) | ERROR

---

### G-05 · HedgeClient-Interface (MiCA Art. 45) — MITTEL

- `HedgeClient` Interface + `MockHedgeClient`
- Stub für DZ-BANK-Treasury-Anbindung (prod: `HttpDzBankHedgeClient` nicht implementiert)

---

### G-06 · Slippage-Schutz (MiCA Art. 23) — MITTEL

- `SlippageExceededException` → HTTP 422 BIZ_005
- 100 BPS Default aus TenantSettings

---

### G-07 · Kill Switch (DORA Art. 17, §25a KWG) — KRITISCH

- `KillSwitchFilter` → blockiert alle schreibenden Requests
- Global (`system_control`) + Mandanten-Ebene (`tenant_settings.kill_switch_active`)
- HTTP 503 SYSTEM_003

---

### G-08 · LimitResolver (§3 GwG) — MITTEL

- `LimitResolver`: CustomerOverride ≤ TenantMax ≥ TenantDefault
- `limit_change_log` Tabelle (V16)

---

### G-09 · Idempotenz-TTL (PSD2) — LOW

- `idempotency_expires_at` (V16), 30-Tage-Default
- `IdempotencyCleanupService` @Scheduled(02:03)

---

### G-10 · CAMT.029 Rejection — MITTEL

- `ExportService.generateCamt029()` — ISO 20022 CAMT.029.001.09
- `GET /api/v1/b2b/export/camt029?since=...`

---

### G-11 · Outbox-Monitor (§25a KWG) — KRITISCH

- `OutboxMonitorService` @Scheduled alle 5 Min
- `N8nWebhookClient.notifyOutboxAlert()` bei PENDING > 15 Min

---

### G-12 · Travel Rule (FATF Rec. 16) — MITTEL

- `beneficiaryName/Address/AccountId` Pflichtfelder bei Transfers > `travel_rule_threshold_eur` (V17)
- `tenant_settings.travel_rule_enabled` (Default: false)

---

### G-13 · Bulk-Erfolgsquote — MITTEL

- `bulk_min_success_rate` in TenantSettings (V16)
- `BulkPaymentThresholdException` → HTTP 422 BIZ_006

---

### G-14 · Telefonnummer-Hash (DSGVO Art. 32) — KRITISCH

- `PhoneHashService` HMAC-SHA256 mit `PHONE_HMAC_KEY` Env-Var
- `phone_alias.phone_hash_algorithm` (V16)
- **Prod-Aktion erforderlich:** `PHONE_HMAC_KEY` setzen!

---

### G-15 · Jahresabschluss Yield (EStG §11, HGB §252) — MITTEL

- `YieldYearEndService` @Scheduled(31.12. 23:30)
- `yield_position.year_end_valuation_eur/last_valued_year` (V18)
- AtruviaTaxClient-Delegation

---

## 5. Flyway-Migrationen Gesamt (V1–V18)

| V | Datei | Inhalt |
|---|---|---|
| V1 | `init.sql` | Alle 8 Basistabellen + Seed-Konten |
| V2 | `fix_b2b_approval_threshold.sql` | tx_limit_single → 25.000 EUR |
| V3 | `add_institutional_address_book.sql` | Institutionelle Whitelist |
| V4 | `add_hold_id_to_transaction.sql` | hold_id Spalte |
| V5 | `update_transaction_status_enum.sql` | Status-Enum erweitert |
| V6 | `add_yield_position.sql` | Yield-Sparkonto-Tabelle |
| V7 | `refactor_audit_log.sql` | AuditLog fromStatus/toStatus als Enum-Spalten |
| V8 | `enable_row_level_security.sql` | RLS-Policies + stablecoin_app-Grants + tenant-Tabelle |
| V9 | `add_inbound_status_values.sql` | INCOMING/COMPLIANCE_PENDING/APPROVED/REJECTED |
| V10 | `enterprise_payment_features.sql` | parent_tx_id, INBOUND_RETURN, UNASSIGNED, RETURNED, Sammelkonto |
| V11 | `buchungskreislauf_spalten.sql` | gross_debit, fee_amount, ledger_booking_reference, tax_withheld |
| V12 | `tenant_settings_und_kill_switch.sql` | tenant_settings + system_control |
| V13 | `tax_event.sql` | tax_event (Drittsystem-Audit-Nachweis) |
| V14 | `reconciliation_run.sql` | Tagesabschluss-Tabelle |
| V15 | `grant_system_control_to_app_user.sql` | GRANTs für neue Tabellen → stablecoin_app |
| V16 | `operational_gaps.sql` | phone_hash_algorithm, limit_change_log, bulk_min_success_rate, idempotency_expires_at |
| V17 | `travel_rule.sql` | beneficiary_* Spalten, travel_rule_enabled in tenant_settings |
| V18 | `yield_year_end.sql` | year_end_valuation, redeem_tx_id nullable |

---

## 6. Neue Fehler-Codes

| Code | HTTP | Exception | Auslöser |
|---|---|---|---|
| AUTH_002 | 401 | `WebhookSignatureException` | Ungültige/fehlende Webhook-Signatur |
| BIZ_005 | 422 | `SlippageExceededException` | Kursabweichung > Slippage-Limit |
| BIZ_006 | 422 | `BulkPaymentThresholdException` | Bulk-Erfolgsquote < Mindest |
| SYSTEM_003 | 503 | `PaymentSystemFrozenException` | Kill Switch aktiv |
| FATF_001 | 400 | `IllegalArgumentException` | Travel Rule Pflichtfelder fehlen |

---

## 7. Test-Coverage (125 Tests, 0 Failures)

| Klasse | Art | TCs |
|---|---|---|
| `WebhookSecurityTest` | Unit | 5 |
| `OutboxProcessorTest` | Unit | 13 |
| `TenantSettingsTest` | Unit | 3 |
| `MockAtruviaTaxClientTest` | Unit | 3 |
| `B2cYieldServiceTest` | Unit | 4 |
| `B2bStateMachineTest` | Unit | 14 |
| `ComplianceServiceTest` | Unit | 5 |
| `AddressBookServiceTest` | Unit | 7 |
| `InboundProcessingTest` | Integration | 2 |
| `EnterprisePaymentFeaturesTest` | Integration | 5 |
| `MultiTenancyIntegrationTest` | Integration | 5 |
| `B2bTransferIntegrationTest` | Integration | 4 |
| `B2bResilienceTest` | Integration | 4 |
| Weitere Unit-Tests | Unit | 51 |

---

## 8. Prod-Deployment-Checkliste

- [ ] `CIRCLE_WEBHOOK_SECRET` Env-Variable setzen (Webhook-Signaturvalidierung)
- [ ] `PHONE_HMAC_KEY` Env-Variable setzen (min. 32 Zufallsbytes hex-kodiert)
- [ ] PostgreSQL `max_connections` ≥ 200 (aktuell: 200, war 100)
- [ ] `tenant_settings` für alle Produktiv-Tenants befüllen
- [ ] `system_control` GLOBAL-Eintrag vorhanden (`INSERT INTO system_control(scope) VALUES('GLOBAL')`)
- [ ] `stablecoin_app` User BYPASSRLS: **nein** (korrekt — RLS bleibt aktiv)
- [ ] `stablecoin` User BYPASSRLS: **ja** (korrekt für Flyway + adminJdbcTemplate)
- [ ] n8n-Webhook-URL konfiguriert für Outbox-Monitor-Alerts

---

*Generiert: 2026-08-18 | Version: Sprint-Abschluss | Tests: 125 | 0 Failures*

---
---

# QA Review — Multi-Token-Adapter + DvP Escrow Engine
## Atruvia Stablecoin Integration Platform — Sprint 2026-08-20

> **Klassifizierung:** INTERN · Für Code-Review und BaFin-Auditierung  
> **Reviewer:** Principal Cloud-Native & Multi-Token Architect  
> **Commit:** `feat: implement multi-token adapter pattern and atomic DvP escrow engine`  
> **Tests:** 232 | 0 Failures | 0 Errors  
> **Flyway:** V1–V20 (V20 neu: dvp_escrow + RLS + Grants)

---

## 1. Executive Summary

Dieser Sprint transformiert die Plattform von einer "Circle-only"-Architektur zu einer flexiblen Multi-Token/Multi-Issuer-Plattform und implementiert den geschäftskritischen DvP-Use-Case:

1. **Multi-Token-Adapter-Pattern** — Erweiterbare Issuer-Abstraktion (Circle, AllUnity EURAU, Qivalis EURQ)
2. **DvP Escrow Engine** — Atomarer Sperr-/Freigabemechanismus für tokenisierte Wertpapiergeschäfte (Herstatt-Risiko-Eliminierung)
3. **StablecoinCurrency-Erweiterung** — EURAU (AllUnity, BaFin-reguliert) und EURQ (DZ Bank Konsortium)

---

## 2. Commit-Übersicht

| Commit | Beschreibung |
|---|---|
| `fda4f49` | fix: TC-04 RLS-Isolation + stablecoin_app User + V19 Grant |
| *(dieser Commit)* | feat: Multi-Token-Adapter-Pattern + Atomic DvP Escrow Engine |

---

## 3. Neue Features & Use Cases

### UC-32 · Multi-Token-Adapter-Routing

**Token-Adapter-Interface:** `StablecoinTokenAdapter` mit `initiateAndConfirm()` + `initiateReturn()`

**Router:** `TokenAdapterRouter` — Spring-Bean, baut automatisch Registry aus allen `StablecoinTokenAdapter`-Beans

**Adapter:**

| Adapter | Währungen | Besonderheit |
|---------|-----------|-------------|
| `CircleTokenAdapter` | USDC, EURC | submit + poll-Zyklus (verschoben aus B2bTransferService) |
| `AllUnityTokenAdapter` | EURAU | BaFin-Deckungsprüfung MiCA Art. 36 (≥105%) |
| `QivalisTokenAdapter` | EURQ | 2-of-N Multisig simuliert (DZ Bank Konsortium) |

**Refactoring B2bTransferService:**
- `submitToCircle()` → `submitToAdapter()` (gleiche Resilience4j-Config `circle-wallet`)
- `pollCircleStatus()` entfernt (Polling lebt jetzt in `CircleTokenAdapter.initiateAndConfirm()`)
- `CircleWalletClient`-Feld ersetzt durch `TokenAdapterRouter`

**Refactoring InboundProcessingService:**
- `circleWalletClient.initiateTransfer()` in `initiateInboundReturn()` → `tokenAdapterRouter.getAdapter(currency).initiateReturn(...)`

---

### UC-33/34/35 · DvP Escrow Engine

**Datenmodell:** `dvp_escrow` (V20, RLS aktiv, analog V8-Muster)

**Zustandsmaschine:**
```
ESCROWED → SETTLED   (Wertpapier erfolgreich übertragen, Stablecoins freigegeben)
ESCROWED → CANCELLED (Wertpapierübertragung gescheitert, EUR zurück auf Kundenkonto)
```

**Endpunkte:**

| Endpoint | UC | HTTP | Beschreibung |
|----------|----|------|-------------|
| `POST /api/v1/b2b/dvp/lock` | UC-33 | 201 | Betrag sperren, Hold anlegen |
| `POST /api/v1/b2b/dvp/settle` | UC-34 | 200 | Stablecoins freigeben, Fee buchen |
| `POST /api/v1/b2b/dvp/cancel` | UC-35 | 200 | Hold aufheben, EUR zurück |

**RLS-Sicherheit:** `TenantEntityListener` setzt `tenant_id` bei `@PrePersist`. Mandant A kann Mandant B's Escrow nicht settle/cancel → HTTP 404 (PostgreSQL RLS).

---

## 4. Flyway-Migrationen (neu)

| Version | Datei | Inhalt |
|---------|-------|--------|
| V19 | `V19__grant_limit_change_log_to_app_user.sql` | GRANT für `limit_change_log` (fehlte seit V16) |
| V20 | `V20__dvp_escrow_and_multi_token.sql` | `dvp_escrow` Tabelle, RLS, Grants, Indexes |

---

## 5. Neue Fehler-Codes

| Code | HTTP | Exception | Auslöser |
|------|------|-----------|---------|
| `NOT_FOUND_001` | 404 | `NoSuchElementException` | DvP-Escrow nicht gefunden / RLS-Isolation |
| `BIZ_001` | 400 | `IllegalStateException` | Escrow nicht in ESCROWED-Status (Double-Settle/Cancel) |

---

## 6. Test-Coverage

| Klasse | Art | TCs | Scope |
|--------|-----|-----|-------|
| `MultiTokenAdapterTest` | Unit (SpringBootTest/NONE) | 6 | Token-Adapter-Routing, BaFin-Coverage |
| `DvpProcessingTest` | Integration (MockMvc + DB) | 8 | DvP-Zustandszyklus, RLS-Isolation |

**Gesamt: 232 Tests | 232 bestanden | 0 Failures | Δ+14 Tests**

---

## 7. Prod-Deployment-Checkliste (Erweiterung)

- [ ] Flyway V20 ausgeführt (`dvp_escrow` Tabelle + RLS + Grants)
- [ ] `AllUnityTokenAdapter` durch `HttpAllUnityClient` ersetzen (prod-Profil analog `HttpCircleWalletClient`)
- [ ] `QivalisTokenAdapter` durch `HttpQivalisClient` ersetzen (prod-Profil)
- [ ] AllUnity API-Credentials konfigurieren (`app.allunity.api-key`, `app.allunity.base-url`)
- [ ] Qivalis Consortium-Endpoint konfigurieren (`app.qivalis.rpc-url`, `app.qivalis.signing-key`)
- [ ] DvP-Fee-Konfiguration in `tenant_settings.fee_flat_b2b_eur` prüfen (Default: 2.50 EUR)
- [ ] `DVP_ESCROW_INTERNAL_ACCOUNT` im CoreBanking-System anlegen
- [ ] Alle bisherigen Checklisten-Punkte aus vorherigem Sprint beachten

---

*Generiert: 2026-08-20 | Version: Sprint Multi-Token + DvP | Tests: 232 | 0 Failures*
