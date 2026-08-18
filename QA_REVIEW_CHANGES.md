# QA Review — Sprint 2026-08-18
## Atruvia Stablecoin Integration Platform

> **Review-Datum:** 2026-08-18  
> **Reviewer:** QS-Team / Principal Engineering  
> **Commits:** `68bb995` (Multi-Tenancy RLS) · `a607b2e` (Inbound Processing)  
> **Branch:** `main` — https://github.com/potty32/stablecoin-integration

---

## 1. Executive Summary

| Feature | Status | Tests | Risiko |
|---|---|---|---|
| Multi-Tenancy (RLS) | ✅ Implementiert & getestet | 5 TCs grün | Mittel — DB-Schema-Breaking-Change |
| Inbound Webhook Processing | ✅ Implementiert & getestet | 2 TCs grün | Niedrig — neuer Pfad, kein Eingriff in Outbound |
| Bestehende Tests | ✅ Alle grün | 99 → 106 gesamt | — |
| Flyway Migrationen | ✅ V8 + V9 clean | — | — |

---

## 2. Architektur-Modifikationen

### 2.1 Multi-Tenancy via PostgreSQL Row-Level Security (Commit `68bb995`)

**Ziel:** Vollständige Datenisolation zwischen Volksbank-Mandanten auf DB-Ebene.

**Muster:** Shared-Database + Logical Tenant Discriminator + PostgreSQL RLS als Defense-in-Depth.

#### 2.1.1 Datenbanknutzer-Trennung

```
stablecoin       → Migration-Owner (DDL, BYPASSRLS via ALTER ROLE)
stablecoin_app   → App-Runtime  (kein BYPASSRLS, unterliegt RLS-Policies)
```

**Spring-Konfiguration (application-dev.yml):**
```yaml
spring:
  datasource:             # JPA/Hibernate → stablecoin_app (RLS aktiv)
    username: stablecoin_app
  flyway:                 # Flyway-Migrationen → stablecoin (BYPASSRLS)
    user: stablecoin
```

#### 2.1.2 Tenant-Propagation (Request → DB)

```
HTTP Request
  │ JWT: {sub: "cust-b2b-001", tenant: "tenant-kleine-vb", ...}
  ▼
JwtAuthFilter
  └── TenantContext.set("tenant-kleine-vb")  [ThreadLocal]
  ▼
TenantAwareDataSource.getConnection()
  └── set_config('app.current_tenant', 'tenant-kleine-vb', false)  [Session-Level]
  ▼
PostgreSQL RLS-Policy
  └── USING (tenant_id = current_setting('app.current_tenant', true))
  ▼
Nur Rows mit tenant_id = 'tenant-kleine-vb' sichtbar
  ▼
JwtAuthFilter.finally → TenantContext.clear()
```

#### 2.1.3 Automatisches tenant_id-Setzen

`TenantEntityListener.@PrePersist` liest `TenantContext.get()` → setzt `entity.tenantId` automatisch auf allen 5 RLS-Tabellen.

#### 2.1.4 Webhook-Spezialfall (Cross-Tenant-Lookup)

Inbound-Webhooks tragen keinen JWT → kein initialer TenantContext.

```java
// adminJdbcTemplate (stablecoin, BYPASSRLS) für initialen Wallet-Lookup
Map<String, Object> row = adminJdbcTemplate.queryForList(
    "SELECT id, tenant_id FROM customer_account WHERE wallet_address = ?", walletId).get(0);
TenantContext.set((String) row.get("tenant_id"));
// → Alle weiteren Operationen laufen mit korrektem Tenant-Kontext
```

---

### 2.2 Inbound Stablecoin Processing (Commit `a607b2e`)

**Ziel:** Empfang von USDC/EURC auf Kunden-Wallets mit Post-Receive AML-Screening und automatischer Core-Banking-Gutschrift.

#### 2.2.1 Prozessablauf

```
POST /api/v1/b2b/inbound/webhook (Circle/Taurus → Bank)
  │
  ├─ 1. Cross-Tenant Account-Lookup (adminJdbcTemplate, BYPASSRLS)
  ├─ 2. TenantContext.set(account.tenantId)
  ├─ 3. Idempotenz: findByBlockchainHash → 409 wenn Duplikat
  ├─ 4. TX INSERT: CREATED → INCOMING [REQUIRES_NEW]
  │       OutboxMsg: PROCESS_INBOUND_COMPLIANCE (Crash-Recovery)
  │
  ├─── LOW/MEDIUM-RISK-Pfad ─────────────────────────────────────────────
  │     COMPLIANCE_PENDING → Chainalysis (direction="incoming")
  │     FX-Konvertierung: EURC×1.0 | USDC×ECB-Rate (kein Spread!)
  │     CoreBankingClient.createLedgerBooking() → EUR-Gutschrift
  │     → COMPLIANCE_APPROVED → SETTLED
  │
  └─── HIGH-RISK-Pfad (z.B. 0xDEAD...) ──────────────────────────────────
        COMPLIANCE_PENDING → Chainalysis → BLOCKED
        AuditLog: action='AML_INBOUND_BLOCK' (BaFin-pflichtiger Eintrag)
        → COMPLIANCE_REJECTED → FAILED
        (Gelder verbleiben auf Wallet — KEINE Gutschrift)
```

#### 2.2.2 State Machine Extension

```
Vorher (Outbound-Pfad):
CREATED → [PENDING_APPROVAL] → COMPLIANCE_CHECKED → FUNDS_HELD → SUBMITTED → SETTLED

Neu (Inbound-Pfad, parallel):
CREATED → INCOMING → COMPLIANCE_PENDING → COMPLIANCE_APPROVED → SETTLED
                                         → COMPLIANCE_REJECTED → FAILED
```

#### 2.2.3 Crash-Recovery (Transactional Outbox)

`OutboxProcessor.PROCESS_INBOUND_COMPLIANCE`:
- TX in Status `INCOMING` beim Neustart → Compliance-Flow wird automatisch neu gestartet
- TX in Terminal-Status → OutboxMsg als SENT markiert (kein Double-Processing)

---

## 3. Schnittstellen-Verträge (API Contracts)

### 3.1 Neuer Endpunkt: Dev-Token (nur devMode)

```
GET /api/v1/auth/dev-token?customerId={id}&tenant={tenantId}
Authorization: keiner (permitAll)
```

**Request (Query-Parameter):**

| Parameter | Pflicht | Werte |
|---|---|---|
| `customerId` | Ja | `cust-b2b-001` \| `cust-b2c-001` |
| `tenant` | Nein | `tenant-kleine-vb` \| `tenant-grosse-vb` \| `tenant-marktbank` \| `tenant-default` |

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJjdXN0LWIyYi0wMDEi...",
  "tenant": "tenant-kleine-vb",
  "customerId": "cust-b2b-001"
}
```

**JWT-Payload (dekodiert):**
```json
{
  "sub": "cust-b2b-001",
  "tenant": "tenant-kleine-vb",
  "iat": 1755473400,
  "exp": 1755559800
}
```

---

### 3.2 Neuer Endpunkt: Inbound Webhook

```
POST /api/v1/b2b/inbound/webhook
Content-Type: application/json
Authorization: keiner (permitAll — in Prod via HMAC-Signatur absichern)
```

**Request-Body:**
```json
{
  "walletId":      "0xBankB2BWallet000000000000000000000000001",
  "amount":        1000.00,
  "currency":      "USDC",
  "blockchainHash":"0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890",
  "senderWallet":  "0xA100000000000000000000000000000000000001"
}
```

| Feld | Typ | Pflicht | Beschreibung |
|---|---|---|---|
| `walletId` | String | Ja | Empfänger-Wallet (muss in `customer_account.wallet_address` existieren) |
| `amount` | BigDecimal | Ja | Empfangener Betrag |
| `currency` | String | Ja | `"USDC"` oder `"EURC"` |
| `blockchainHash` | String | Ja | On-Chain-TX-Hash (Idempotenz-Schlüssel) |
| `senderWallet` | String | Ja | Absender-Wallet (wird AML-gescreent) |

**Response — LOW_RISK (201 Created):**
```json
{
  "transactionId": "0c38e361-1cc9-46e4-adf3-7cb3b66c86d8",
  "type": "INBOUND",
  "status": "SETTLED",
  "amountFiat": 1082.300000,
  "amountStablecoin": 1000.000000,
  "currency": "USDC",
  "blockchainHash": "0xabcdef...",
  "grossRevenue": 0.0,
  "requiresApproval": false,
  "createdAt": "2026-08-18T02:18:18.972639",
  "settledAt": null,
  "timeline": []
}
```

**Response — HIGH_RISK (201 Created, Funds BLOCKED):**
```json
{
  "transactionId": "633239b2-b494-4035-8e97-9d8341ff6886",
  "type": "INBOUND",
  "status": "FAILED",
  "amountFiat": 500.000000,
  "amountStablecoin": 500.000000,
  "currency": "EURC",
  "blockchainHash": "0xdead...",
  "grossRevenue": 0.0,
  "requiresApproval": false,
  "createdAt": "2026-08-18T02:18:19.613696",
  "settledAt": null,
  "timeline": []
}
```

**Fehler-Responses:**

| HTTP | Code | Ursache |
|---|---|---|
| 404 | `NOT_FOUND_001` | `walletId` nicht in DB (kein Kunden-Account für diese Wallet) |
| 409 | `IDEMPOTENCY_001` | `blockchainHash` bereits verarbeitet |
| 500 | — | Unerwarteter Systemfehler (Backend-Log prüfen) |

---

## 4. Datenbank-Schema

### 4.1 Flyway V8 — Multi-Tenancy (2026-08-18)

#### 4.1.1 Neue Tabelle: `tenant`

```sql
CREATE TABLE tenant (
    id         VARCHAR(50)  PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    type       VARCHAR(20)  NOT NULL,
    rls_active BOOLEAN      NOT NULL DEFAULT true
);
```

**Dev-Seed-Daten:**

| id | name | type |
|---|---|---|
| `tenant-kleine-vb` | Volksbank Kleinstadt eG | COOPERATIVE |
| `tenant-grosse-vb` | Volksbank Metropole eG | COOPERATIVE |
| `tenant-marktbank` | Marktbank AG | BANK |
| `tenant-default` | Default Dev Tenant | DEV |

#### 4.1.2 Erweiterte Tabellen: `tenant_id`-Spalte (5 Tabellen)

```sql
-- Auf jeder der 5 Tabellen:
ALTER TABLE {tabelle} ADD COLUMN tenant_id VARCHAR(50)
    NOT NULL REFERENCES tenant(id);
```

Betroffene Tabellen: `customer_account`, `stablecoin_transaction`, `address_book`, `yield_position`, `audit_log`

Backfill: alle bestehenden Rows → `tenant_id = 'tenant-default'`

#### 4.1.3 Row-Level Security

```sql
-- Für alle 5 Tabellen:
ALTER TABLE {tabelle} ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_policy ON {tabelle}
    USING (tenant_id = current_setting('app.current_tenant', true));
```

**Isolation-Beweis (E2E-Test):**
```bash
# Mandant A sieht SEINEN Account:
TOKEN_A="...tenant=tenant-kleine-vb..."
GET /api/v1/accounts/DE89370400440532013000/balance → 200 OK (Kontostand)

# Mandant B sieht ANDEREN Account NICHT:
TOKEN_B="...tenant=tenant-grosse-vb..."
GET /api/v1/accounts/DE89370400440532013000/balance → 404 (RLS filtert)
```

---

### 4.2 Flyway V9 — Inbound Status Values (2026-08-18)

```sql
-- V5 CHECK-Constraint (11 Werte) → 15 Werte
ALTER TABLE stablecoin_transaction
    DROP CONSTRAINT stablecoin_transaction_status_check;

ALTER TABLE stablecoin_transaction
    ADD CONSTRAINT stablecoin_transaction_status_check
    CHECK (status IN (
        'CREATED','PENDING_APPROVAL','APPROVED','REJECTED','EXPIRED',
        'COMPLIANCE_CHECKED','FUNDS_HELD','SUBMITTED','SETTLED','REDEEMED','FAILED',
        'INCOMING','COMPLIANCE_PENDING','COMPLIANCE_APPROVED','COMPLIANCE_REJECTED'
    ));
```

**Neue Status-Werte:**

| Status | Bedeutung | Terminal? |
|---|---|---|
| `INCOMING` | Blockchain-Eingang registriert | Nein |
| `COMPLIANCE_PENDING` | Post-Receive AML-Prüfung läuft | Nein |
| `COMPLIANCE_APPROVED` | AML erfolgreich → Gutschrift | Nein |
| `COMPLIANCE_REJECTED` | AML blockiert → führt zu FAILED | Nein (→ FAILED) |

---

### 4.3 Vollständiger Flyway-Migrations-Stack

| Version | Datei | Status |
|---|---|---|
| V1 | `V1__init.sql` | ✅ |
| V2 | `V2__fix_b2b_approval_threshold.sql` | ✅ |
| V3 | `V3__add_institutional_address_book.sql` | ✅ |
| V4 | `V4__add_hold_id_to_transaction.sql` | ✅ |
| V5 | `V5__update_transaction_status_enum.sql` | ✅ |
| V6 | `V6__add_yield_position.sql` | ✅ |
| V7 | `V7__refactor_audit_log.sql` | ✅ |
| V8 | `V8__enable_row_level_security.sql` | ✅ **NEU** |
| V9 | `V9__add_inbound_status_values.sql` | ✅ **NEU** |

---

## 5. Sicherheits- & Resilienz-Metriken

### 5.1 Testabdeckung

**Gesamt: 106 Tests | 0 Failures | 0 Errors | 0 Skipped**

| Test-Klasse | TCs | Status | Bereich |
|---|---|---|---|
| B2bStateMachineTest | 27 | ✅ | State Machine (inkl. neue Inbound-Übergänge) |
| OutboxProcessorTest | 11 | ✅ | Crash-Recovery (inkl. PROCESS_INBOUND_COMPLIANCE) |
| BulkPaymentServiceTest | 10 | ✅ | Bulk CSV |
| GlobalExceptionHandlerTest | 10 | ✅ | Fehler-Handling |
| ExportServiceTest | 8 | ✅ | CAMT.053 + DATEV |
| AddressBookServiceTest | 7 | ✅ | Whitelist-Management |
| InstitutionalAddressBookServiceTest | 6 | ✅ | Bank-weite Whitelist |
| B2cYieldServiceTest | 5 | ✅ | Yield-Sparkonto |
| **MultiTenancyIntegrationTest** | **5** | **✅ NEU** | RLS-Isolation |
| ComplianceServiceTest | 5 | ✅ | AML (inkl. direction-Parameter) |
| B2bTransferIntegrationTest | 4 | ✅ | Transfer-Flow |
| B2bResilienceTest | 4 | ✅ | Circuit Breaker + Idempotenz |
| CommonControllerOwnershipTest | 2 | ✅ | Ownership-403 |
| **InboundProcessingTest** | **2** | **✅ NEU** | Webhook-Verarbeitung |
| **Gesamt** | **106** | **✅** | |

**Coverage (Baseline 2026-08-17 + neue Klassen):**

| Metrik | Baseline (2026-08-17) | Heute (Schätzung) |
|---|---|---|
| LINE | 62,7% | ~65% (+InboundProcessingService) |
| BRANCH | 48,4% | ~50% |
| CLASS | 86,7% | ~88% |

> Exakte Zahlen: `mvn verify -Pcoverage` (Jacoco-Report in `target/site/jacoco/`)

---

### 5.2 Sicherheitsvalidierung — RLS-Isolation

**TC: Mandant B kann Mandant A's Account NICHT einsehen**
```
Tenant A:  JWT{sub="test-a-XXX", tenant="tenant-kleine-vb"}
Tenant B:  JWT{sub="test-b-XXX", tenant="tenant-grosse-vb"}

Tenant A erstellt Transfer → TX mit tenant_id='tenant-kleine-vb'
Tenant B fragt GET /api/v1/b2b/transfers → HTTP 200, content=[]  (leer)
Tenant B fragt TX per ID → HTTP 4xx/5xx (RLS filtert + Ownership-Check)
```

**DB-Beweis der Isolation:**
```sql
-- Als stablecoin_app mit app.current_tenant = 'tenant-grosse-vb':
SELECT COUNT(*) FROM stablecoin_transaction
WHERE customer_account_id = '<accountA_id>';
-- → 0 (RLS filtert alle Tenant-A Transaktionen aus)
```

---

### 5.3 AML-Block-Validierung (Inbound HIGH_RISK)

**Test-Adresse:** `0xDEAD000000000000000000000000000000000000` (Chainalysis-Mock → HIGH_RISK)

**Verhalten:**
1. TX wird als `INCOMING` persistiert (Blockchain-Nachweis)
2. AML-Screen → BLOCKED
3. AuditLog INSERT: `action='AML_INBOUND_BLOCK'` (BaFin-pflichtiger Revisionsnachweis)
4. TX → `COMPLIANCE_REJECTED` → `FAILED`
5. **Keine** `CoreBankingClient.createLedgerBooking()` Ausführung
6. **Keine** EUR-Gutschrift auf Kundenkonto

---

### 5.4 Resilience — Crash-Recovery

**Szenario:** System crasht nach `INCOMING` (vor Compliance-Screen)

**Recovery:**
1. Backend-Neustart
2. `OutboxProcessor.processPendingMessages()` (alle 5s)
3. Event-Typ `PROCESS_INBOUND_COMPLIANCE` erkannt
4. TX-Status `INCOMING` → Compliance-Flow erneut starten
5. Bei SETTLED/FAILED/terminal → OutboxMsg als `SENT` markiert

**Idempotenz:** Compliance-Flow kann mehrfach für dieselbe TX aufgerufen werden. Status `COMPLIANCE_PENDING` schützt gegen Double-Processing (transitionTo würde `IllegalStateException` werfen).

---

### 5.5 Bekannte Einschränkungen (offene Punkte)

| # | Bereich | Beschreibung | Priorität |
|---|---|---|---|
| 1 | Webhook-Security | Keine HMAC-Signaturprüfung (`X-Circle-Signature`) — Prod: HMAC-Validierung implementieren | 🔴 Hoch |
| 2 | Rate Limiting | Webhook-Endpunkt ohne Rate-Limit — DoS möglich | 🔴 Hoch |
| 3 | Inbound SETTLED | `settledAt`-Timestamp wird nicht gesetzt (Outbound: korrekt gesetzt) | 🟡 Mittel |
| 4 | RLS (approval_workflow, outbox) | Tabellen ohne `tenant_id` — institutionell akzeptabel, da bank-intern | 🔵 Niedrig |
| 5 | Test-Coverage | `controller/b2b` (14 Endpunkte) nicht via MockMvc gedeckt | 🔴 Hoch |
| 6 | B2b-Empfang | Kein UI-Widget für Inbound-Transaktionen in TransferListComponent | 🟡 Mittel |

---

## 6. Neue Entitäten & Klassen (Vollständige Liste)

### Backend — Neue Klassen (2026-08-18)

| Klasse | Paket | Zweck |
|---|---|---|
| `TenantContext` | `config` | ThreadLocal-Tenant-Träger |
| `TenantAwareDataSource` | `config` | DataSource-Proxy: set_config bei getConnection() |
| `TenantDataSourceConfig` | `config` | Spring-Bean: Primary DataSource + adminDataSource |
| `TenantEntityListener` | `config` | JPA @PrePersist: tenant_id auto-setzen |
| `TenantAspect` | `config` | Guard-Layer: loggt fehlenden TenantContext |
| `Tenant` | `entity` | JPA-Entity für `tenant`-Tabelle |
| `TenantRepository` | `repository` | CRUD für `tenant` |
| `DevAuthController` | `controller` | Dev-Token-Endpoint (devMode only) |
| `InboundWebhookRequest` | `dto/request` | Webhook-Payload-Record |
| `InboundWebhookController` | `controller` | POST /webhook |
| `InboundProcessingService` | `service/inbound` | Inbound-Flow-Orchestrierung |

### Backend — Modifizierte Klassen (2026-08-18)

| Klasse | Änderung |
|---|---|
| `JwtAuthFilter` | Extrahiert `tenant`-Claim, setzt TenantContext |
| `SecurityConfig` | permitAll für `/auth/dev-token` + `/inbound/webhook` |
| `TransactionStatus` | +4 Inbound-Werte (INCOMING, COMPLIANCE_PENDING, COMPLIANCE_APPROVED, COMPLIANCE_REJECTED) |
| `B2bTransferService` | ALLOWED_TRANSITIONS: +5 Inbound-Einträge |
| `ComplianceService` | `direction`-Parameter (Breaking Change für 1 Aufrufer) |
| `OutboxProcessor` | +`PROCESS_INBOUND_COMPLIANCE` Recovery-Handler |
| 5 Entities | `@EntityListeners` + `tenantId`-Feld (CustomerAccount, StablecoinTransaction, AddressBook, YieldPosition, AuditLog) |
| `CustomerAccountRepository` | +`findByWalletAddress()` |
| `StablecoinTransactionRepository` | +`findByBlockchainHash()` |

### Frontend — Modifizierte Dateien (2026-08-18)

| Datei | Änderung |
|---|---|
| `login.component.ts` | Tenant-Dropdown (3 Volksbanken) + API-Call für JWT |

---

## 7. Review-Checkliste

Für das Code-Review bitte folgende Punkte prüfen:

- [ ] **RLS-Policies:** Stimmen `USING`-Klauseln auf allen 5 Tabellen?
- [ ] **TenantContext.clear()** — wird in allen Request-Pfaden (auch Error-Pfaden) aufgerufen?
- [ ] **Webhook permitAll** — ist die URL-Pattern exakt genug um kein Over-Permitting zu erzeugen?
- [ ] **adminJdbcTemplate** — werden alle Queries sicher parameterisiert (kein SQL-Injection)?
- [ ] **Inbound FX** — USDC×ECB ohne Spread korrekt? (Inbound ist gebührenfrei by Design)
- [ ] **AML_INBOUND_BLOCK** — wird AuditLog wirklich VOR den State-Machine-Transitionen committed?
- [ ] **PROCESS_INBOUND_COMPLIANCE** — OutboxProcessor: wird TenantContext korrekt gesetzt und cleared?
- [ ] **Idempotenz** — blockchainHash als UNIQUE-Idempotenz-Schlüssel ausreichend?
- [ ] **B2bStateMachineTest** — decken die 27 TCs auch die neuen Inbound-Übergänge ab?

---

*Generiert: 2026-08-18 | Atruvia AG — Stablecoin Integration Platform*
