# Handover-Dokument — Atruvia Stablecoin Integration Platform

> Letzte Aktualisierung: **2026-08-20** (feat: Multi-Token-Adapter-Pattern + Atomic DvP Escrow Engine) | GitHub: https://github.com/potty32/stablecoin-integration
> **Commit 2026-08-20 (neu):** feat: Multi-Token-Adapter-Pattern (EURAU/EURQ) + DvP Escrow Engine (UC-32–35) + V20
> **Commit 2026-08-20:** TC-04 Fix (stablecoin_app User + V19 Grant limit_change_log + docker-compose Init-Script)
> **Commit 2026-08-19:** `879c66b` API Rate Limiting (Token-Bucket) + systematische Test-Erweiterung
> **Commits 2026-08-18:** `08a1eb8` Async Kafka/S3 → `930e94c` E2E Cross-Tenant → `1a516ab` Dev-Portal → `887b6f6` Docs → `f31336e` BaFin G-08–G-15
> **Tests:** 232 | **232 bestanden** | 0 Fehler | **Flyway:** V1–V20 | **Frontend:** Angular 18 + Dev-Portal

---

## Initiales Prompt für neue Instanz

Kopiere diesen Block als erste Nachricht in die neue Session:

```
Lies die HANDOVER.md im Repository https://github.com/potty32/stablecoin-integration
vollständig (sie ist dein Kontext-Dokument für diese Session).

Führe danach folgende Setup-Schritte aus (Abschnitt 7 der HANDOVER.md):
1. System-Pakete sicherstellen (Java 21, Maven, PostgreSQL, Node/npm)
2. PostgreSQL starten und DB einrichten
3. Backend bauen (mvn package -DskipTests) und starten (SPRING_PROFILES_ACTIVE=dev)
4. Frontend starten (ng serve)
5. Statusbericht: Health-Endpoint + DB-Tabellen-Check + Frontend erreichbar

Wichtig: Git-Credentials für Push einrichten (einmalig pro Kasm-Session):
  git config --global credential.helper store
  git config --global user.email "dev@atruvia.de"
  git config --global user.name "Atruvia Dev"
  echo "https://potty32:<NEUES_GITHUB_TOKEN>@github.com" > ~/.git-credentials
  chmod 600 ~/.git-credentials

GitHub-Token erzeugen: github.com → Settings → Developer settings →
Personal access tokens → Tokens (classic) → Generate new token → Scope: repo

Danach lies die USE_CASES_v2.md für den vollständigen fachlichen Überblick
und frage mich, womit wir anfangen sollen.
```

---

## 1. Projektkontext

### Auftraggeber & Rolle
- **Atruvia AG** — Digitalisierungspartner der genossenschaftlichen Volksbanken Raiffeisenbanken FinanzGruppe
- Regulatorischer Rahmen: **MiCA** (Markets in Crypto-Assets Regulation, EU 2023/1114), **BaFin IT-Audit**

### Fachliche Vision
- **"Turbo Rail"**: Blockchain-basierte Express-Schiene (USDC/EURC via Circle) parallel zu SWIFT
- Zielgruppen: **B2B** (Firmenkunden) und **B2C** (Privatkunden)
- B2C: Blockchain-Begriffe vollständig abstrahiert (kein "Wallet", "USDC" in der UI)

### Architektur-Standards
- Strikte Schichten: Controller → Service → Repository → Entity
- DTOs (Java Records) — Entities nie direkt ans Frontend
- Profile: `dev` (lokale PostgreSQL, Mocks) | `prod` (Env-Vars, mTLS, echte APIs)

---

## 2. Technologie-Stack (aktuell)

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21, Maven |
| Datenbank | PostgreSQL 16 + Flyway (V1–V9, siehe Abschnitt 12) |
| Frontend | Angular 18, TypeScript, Standalone Components |
| Auth | JWT HS256, `JwtAuthFilter` (dev-mode: "dev-user" wenn kein Token) |
| Externe APIs (Mock) | Circle (USDC/EURC), Taurus (MPC), Chainalysis (AML), n8n, ECB |
| Resilienz | Resilience4j Circuit Breaker + Retry (circle-wallet, taurus-custody, core-banking, chainalysis) |
| Observability | OpenTelemetry Tracing, AuditLog (relationales Schema, V7-Migration) |
| Tests | JUnit 5, Mockito, Testcontainers (disabledWithoutDocker), AbstractLocalDbTest (lokale PG) |

---

## 3. Repository-Struktur

```
stablecoin-integration/
├── backend/src/main/java/de/atruvia/stablecoin/
│   ├── controller/b2b/           # 14 REST-Endpunkte (inkl. institutional-address-book, admin/sanctions-scan)
│   ├── controller/b2c/           # B2C-Endpunkte (remittances, p2p, yield, micropayments)
│   ├── controller/common/        # /accounts/{iban}/balance (Ownership-Check!) + /transactions/{id}
│   ├── service/b2b/
│   │   ├── B2bTransferService.java   # State Machine (transitionTo, ALLOWED_TRANSITIONS, CB+Retry-Wrapper)
│   │   ├── AddressBookService.java   # Chainalysis-Screen (Fail-Closed)
│   │   ├── InstitutionalAddressBookService.java  # Bank-weite Whitelist (NEU)
│   │   ├── SanctionsBatchService.java            # Nachtlicher Batch-Job (NEU)
│   │   ├── ExportService.java        # CAMT.053 + DATEV
│   │   └── BulkPaymentService.java
│   ├── service/b2c/
│   │   ├── B2cYieldService.java      # YieldPosition-Entity-basiert (saubere Trennung Deposit/Redeem)
│   │   ├── B2cRemittanceService.java
│   │   ├── B2cP2pService.java
│   │   └── B2cMicropaymentService.java
│   ├── service/compliance/       # ComplianceService (Chainalysis, Circuit Breaker)
│   ├── service/fx/               # FxRateService (EURC=1.0, USDC=ECB live)
│   ├── service/revenue/          # RevenueService (Spread + Fee)
│   ├── config/                       # 🆕 Multi-Tenancy-Infrastruktur
│   │   ├── TenantContext.java            # ThreadLocal<String> für Request-Tenant
│   │   ├── TenantAwareDataSource.java    # set_config('app.current_tenant',?,false) per getConnection()
│   │   ├── TenantDataSourceConfig.java   # Primary DataSource + adminDataSource (BYPASSRLS)
│   │   ├── TenantEntityListener.java     # @PrePersist: tenant_id auto-setzen
│   │   └── TenantAspect.java             # Guard-Layer: loggt fehlenden TenantContext
│   ├── entity/
│   │   ├── TransactionStatus.java    # 15 Werte (V9): +INCOMING, COMPLIANCE_PENDING,
│   │   │                             #   COMPLIANCE_APPROVED, COMPLIANCE_REJECTED
│   │   ├── Tenant.java               # 🆕 JPA-Entity für tenant-Tabelle (V8)
│   │   ├── YieldPosition.java        # ACTIVE → CLOSED (NEU, eigener Lebenszyklus)
│   │   ├── YieldStatus.java          # ACTIVE, CLOSED
│   │   ├── AuditLog.java             # +tenant_id (V8), transactionId FK, fromStatus, toStatus, details (V7)
│   │   └── ...                       # CustomerAccount, StablecoinTransaction, AddressBook, YieldPosition
│   │                                 # alle: +tenant_id Feld + @EntityListeners(TenantEntityListener.class)
│   ├── service/inbound/              # 🆕 Inbound Processing
│   │   └── InboundProcessingService.java  # Webhook→INCOMING→AML→EUR-Gutschrift→SETTLED
│   ├── outbox/
│   │   └── OutboxProcessor.java      # Recovery: recoverSubmitToBlockchain() + recoverInboundCompliance()
│   ├── client/
│   │   ├── FxRateClient.java         # Interface
│   │   ├── mock/MockFxRateClient.java   # 1.0823 (dev)
│   │   └── http/HttpEcbRateClient.java  # ECB SDMX-JSON (prod)
│   └── resources/db/migration/
│       ├── V1__init.sql
│       ├── V2__fix_b2b_approval_threshold.sql
│       ├── V3__add_institutional_address_book.sql
│       ├── V4__add_hold_id_to_transaction.sql
│       ├── V5__update_transaction_status_enum.sql
│       ├── V6__add_yield_position.sql
│       ├── V7__refactor_audit_log.sql
│       ├── V8__enable_row_level_security.sql   # 🆕 tenant-Tabelle + tenant_id + RLS
│       └── V9__add_inbound_status_values.sql   # 🆕 +4 Status-Werte für Inbound
├── backend/src/test/java/de/atruvia/stablecoin/
│   ├── AbstractLocalDbTest.java        # @SpringBootTest mit lokaler PG (kein Docker)
│   ├── AbstractIntegrationTest.java    # Testcontainers (disabledWithoutDocker=true)
│   ├── B2bStateMachineTest.java        # 27 TCs State Machine (inkl. Inbound-Übergänge)
│   ├── B2bTransferIntegrationTest.java # 4 TCs Integration
│   ├── B2bResilienceTest.java          # 4 TCs Resilience (Idempotenz, Recovery)
│   ├── CommonControllerOwnershipTest.java # 2 TCs Ownership
│   ├── B2cYieldServiceTest.java        # 5 TCs Yield Service
│   ├── OutboxProcessorTest.java        # 11 TCs Recovery
│   ├── ComplianceServiceTest.java      # 5 TCs (inkl. direction-Parameter)
│   ├── GlobalExceptionHandlerTest.java # 10 TCs
│   ├── ExportServiceTest.java          # 8 TCs
│   ├── MultiTenancyIntegrationTest.java # 🆕 5 TCs RLS-Isolation
│   ├── InboundProcessingTest.java      # 🆕 2 TCs Webhook (LOW/HIGH_RISK)
│   ├── BulkPaymentServiceTest.java     # 10 TCs
│   ├── AddressBookServiceTest.java     # 7 TCs
│   └── InstitutionalAddressBookServiceTest.java # 6 TCs
├── frontend/src/app/
│   ├── core/services/
│   │   ├── auth.service.ts     # JWT dekodieren, getIban()
│   │   └── transaction.service.ts
│   └── features/
│       ├── b2b/transfer/transfer-list.component.ts  # Balance-Widget (NEU)
│       └── ...
├── USE_CASES.md          # Changelog-Dokumentation aller 26 UCs
├── USE_CASES_v2.md       # Vollständige, aktuelle Doku aller 26 UCs + State Machine + Reliability
├── HANDOVER.md           # Dieses Dokument
└── testdata/seed_dev.sql
```

---

## 4. Fachliche Schlüsselregeln

| Regel | Wert |
|---|---|
| Approval-Threshold (Vier-Augen) | > 25.000 EUR |
| Taurus Single-TX-Limit | 1.000.000 EUR → HTTP 403 (`TAURUS_001`) |
| Circle Mock-Delay (Settlement) | 3 Sekunden → COMPLETE |
| FX Rate Quote Gültigkeit | 60 Sekunden |
| USDC-Rate (dev) | 1.0823 EUR/USD (Mock) — Prod: ECB SDMX-JSON live |
| EURC-Rate | 1.0 (immer fix, 1:1 mit EUR) |
| Phone-Alias Hashing | SHA-256 + Salt `"atruvia-stablecoin-2026"` |
| Yield Rate | 3,5% p.a. (Zinseszins täglich) |
| Yield Entity | `YieldPosition` (ACTIVE→CLOSED), YIELD_DEPOSIT TX bleibt immer SETTLED |
| Micropayment Max | 10 EUR |
| High-Risk Test-Adresse | `0xDEAD000000000000000000000000000000000000` → HTTP 403 |
| AuditLog | INSERT-only, relationales Schema (transactionId FK, fromStatus, toStatus, details) |
| B2B Gebühr | 2,50 EUR + 0,15% FX-Spread |
| B2C Remittance-Gebühr | 0,50 EUR |
| P2P-Gebühr | 0,00 EUR |
| State Machine | `transitionTo()` mit ALLOWED_TRANSITIONS Map — ungültige Übergänge → IllegalStateException |
| Auto-Hold-Release | Bei FAILED aus FUNDS_HELD/SUBMITTED → `CoreBankingClient.releaseHold()` automatisch |

---

## 5. Bekannte Besonderheiten & Architektur-Entscheidungen

### State Machine (B2bTransferService)
11-wertiger `TransactionStatus` mit zentraler `transitionTo()` Methode [REQUIRES_NEW + Pessimistic Lock].
`ALLOWED_TRANSITIONS` Map blockt ungültige Übergänge zur Laufzeit mit `IllegalStateException`.
Alle Statuswechsel via `self.*` aufgerufen (Spring AOP Proxy für REQUIRES_NEW).

### Idempotenz (atomare Race-Condition-Prüfung)
`findByIdempotencyKey()` und TX-Insert laufen in **derselben** `@Transactional` von `persistInitialTransaction()`.
DB-UNIQUE-Constraint auf `idempotency_key` ist zweite Sicherheitslinie.

### Crash-Recovery (Transactional Outbox)
Bei `FUNDS_HELD` wird synchron eine `SUBMIT_TO_BLOCKCHAIN` OutboxMessage committed.
`OutboxProcessor` (alle 5s) liest PENDING-Nachrichten und finalisiert SUBMITTED-TX via Circle-API-Poll.

### Circuit Breaker + Retry
`circle-wallet` und `taurus-custody`: `@Retry` (3x/2x) + `@CircuitBreaker` via `self.*` Wrapper-Methoden.
`IllegalStateException` in `ignore-exceptions` für Retry — Fallbacks werfen es, darf nicht zu weiterem Retry führen.
`ComplianceBlockException` in `ignore-exceptions` für Chainalysis-CB — fachlicher Block öffnet CB nicht.

### YieldPosition (saubere Trennung)
`YIELD_DEPOSIT` TX: unveränderlicher Buchungsbeleg, bleibt immer `SETTLED`.
`YIELD_REDEEM` TX: eigener Auszahlungsbeleg, Status `REDEEMED`.
`YieldPosition` Entity: eigenständiger Lebenszyklus (ACTIVE→CLOSED), kein Status-Missbrauch auf TX-Ebene.

### AuditLog (relationales Schema, V7)
`previous_state`/`new_state` JSONB entfernt. Neue Spalten: `transaction_id` (FK + Index), `from_status`, `to_status`, `details` (TEXT).
`buildTimeline()` ist ein einfacher DB-Query ohne Regex. Timeline in `TransactionResponse.TimelineEntry`.

### Ownership-Check
`CommonController.getBalance()` und `getTransaction()` prüfen via `auth.getName()` → `findByCustomerId()` ob IBAN/TX dem anfragenden User gehört → 403 `AUTH_001` bei Fremdzugriff.

### JwtAuthFilter (Test-Kompatibilität)
Filter prüft zuerst ob `SecurityContextHolder` bereits eine Authentifizierung enthält.
Wenn ja (z.B. `@WithMockUser` in Tests) → Filter wird übersprungen, kein Dev-Mode-Override.

### Institutionelle Whitelist (OR-Logik)
Transfer-Whitelist-Check: Kunden-Adressbuch **ODER** institutionelle Whitelist.
`InstitutionalAddressBook` hat kein `customer_account_id` FK — bank-weit gültig.

---

## 6. Seed-Accounts (Flyway V1)

| Account | customerId | IBAN | Typ | KYC | TX-Limit |
|---|---|---|---|---|---|
| B2B | cust-b2b-001 | DE89370400440532013000 | B2B | TIER_3 | 25.000 EUR |
| B2C | cust-b2c-001 | DE27200400600532013001 | B2C | TIER_2 | 5.000 EUR |

### JWT erzeugen (Python)
```python
import hmac, hashlib, base64, json, time
secret = "<JWT_SECRET aus application-dev.yml>"  # dev-secret-key-minimum-256-bits-...
now = int(time.time())
h = base64.urlsafe_b64encode(json.dumps({"alg":"HS256","typ":"JWT"}).encode()).rstrip(b"=").decode()
p = base64.urlsafe_b64encode(json.dumps({"sub":"cust-b2b-001","iat":now,"exp":now+86400}).encode()).rstrip(b"=").decode()
sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), f"{h}.{p}".encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
print(f"{h}.{p}.{sig}")
```

---

## 7. Setup auf neuem Rechner (Ubuntu 24.04)

```bash
# 1. System-Pakete
sudo apt-get update -qq
sudo apt-get install -y openjdk-21-jdk maven postgresql postgresql-client nodejs npm

# 2. Repository klonen
git clone https://github.com/potty32/stablecoin-integration.git
cd stablecoin-integration

# 3. Git-Credentials konfigurieren (Token von github.com → Settings → Developer settings)
git config --global credential.helper store
git config --global user.email "dev@atruvia.de"
git config --global user.name "Atruvia Dev"
echo "https://potty32:<TOKEN>@github.com" > ~/.git-credentials
chmod 600 ~/.git-credentials

# 4. PostgreSQL einrichten
sudo service postgresql start
sudo -u postgres psql -c "CREATE USER stablecoin WITH PASSWORD 'stablecoin_dev_pass';"
# WICHTIG: stablecoin_app MUSS vor Flyway-Start existieren (V8 GRANT)
sudo -u postgres psql -c "CREATE USER stablecoin_app WITH PASSWORD 'stablecoin_app_pass';"
sudo -u postgres psql -c "CREATE DATABASE stablecoin_dev OWNER stablecoin;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE stablecoin_dev TO stablecoin;"
sudo -u postgres psql -c "GRANT CONNECT ON DATABASE stablecoin_dev TO stablecoin_app;"

# 5. Backend bauen und starten (Flyway V1-V19 läuft automatisch)
cd backend
mvn package -DskipTests -q
SPRING_PROFILES_ACTIVE=dev nohup java -jar target/stablecoin-backend-1.0.0.jar \
  --server.port=8080 > /tmp/backend.log 2>&1 &
sleep 25 && curl -s http://localhost:8080/actuator/health
# → {"status":"UP"}

# 6. Testdaten einspielen (NACH Backend-Start, Flyway-Schema muss existieren)
cd ..
sudo -u postgres psql -d stablecoin_dev -f testdata/seed_dev.sql

# 7. Frontend starten
cd frontend
npm install
npx ng serve --proxy-config proxy.conf.json --host 0.0.0.0 --port 4200 \
  > /tmp/frontend.log 2>&1 &
sleep 15 && echo "Frontend ready" && curl -s http://localhost:4200 | head -3
```

### Logs
| Komponente | Log |
|---|---|
| Backend | `/tmp/backend.log` |
| Frontend | `/tmp/frontend.log` |

---

## 8. API-Überblick

### B2B (`/api/v1/b2b/`)
| Methode | Pfad | Zweck |
|---|---|---|
| POST | /transfers | Transfer initiieren (`X-Idempotency-Key` Pflicht, atomare Idempotenz) |
| GET | /transfers | Liste (paginiert, `?status=&page=&size=`) |
| GET | /transfers/{id} | Detail + Status-Timeline (fromStatus→toStatus, kein Regex) |
| POST | /transfers/{id}/approve | Vier-Augen-Freigabe (approverId aus JWT) |
| POST | /transfers/{id}/reject | Ablehnung (approverId aus JWT) |
| GET | /rate-quote | FX-Kurs sichern (`?amountEur=&currency=`) — USDC: ECB live |
| POST | /address-book | Adresse whitelisten (Chainalysis, Fail-Closed) |
| GET | /address-book | Kunden-Whitelist auflisten |
| DELETE | /address-book/{id} | Adresse widerrufen |
| POST | /institutional-address-book | Bank-weite institutionelle Whitelist |
| GET | /institutional-address-book | Institutionelle Whitelist auflisten |
| DELETE | /institutional-address-book/{id} | Institutionellen Eintrag widerrufen |
| POST | /bulk-payments | CSV-Upload (multipart/form-data) |
| GET | /export/camt053 | ISO-20022 CAMT.053 |
| GET | /export/datev | DATEV-CSV |
| POST | /admin/sanctions-scan | Sanctions-Batch manuell auslösen |

### B2C (`/api/v1/b2c/`)
| Methode | Pfad | Zweck |
|---|---|---|
| POST | /remittances | Auslandsüberweisung (0,50 EUR Fee) |
| POST | /p2p/phone/register | Telefon-Alias registrieren (SHA-256+Salt) |
| POST | /p2p/phone | P2P via Telefonnummer |
| POST | /savings/yield/deposit | Yield-Einlage → YIELD_DEPOSIT TX + YieldPosition(ACTIVE) |
| GET | /savings/yield | Aktuelle Position (`positionId`, live Zinsen) |
| DELETE | /savings/yield/{positionId} | Auflösen → YIELD_REDEEM TX + YieldPosition(CLOSED) |
| POST | /micropayments | Biometrie-Kleinstzahlung (max. 10 EUR) |

### Gemeinsam (`/api/v1/`)
| Methode | Pfad | Zweck |
|---|---|---|
| GET | /accounts/{iban}/balance | Kontostand (Ownership-Check!) |
| GET | /transactions/{id} | TX-Detail + Timeline (Ownership-Check!) |
| GET | /actuator/health | Health-Check |

---

## 9. Frontend — Login & Auth

- Login-Seite (`/login`): **🏢 Firmenkunde** → JWT für `cust-b2b-001` | **👤 Privatkunde** → JWT für `cust-b2c-001`
- Balance-Widget in `TransferListComponent` (B2B): zeigt EUR + USDC/EURC aus `getAccountBalance()`
- Auth-Guard schützt alle `/b2b/*` und `/b2c/*` Routen
- `AuthService.getIban()`: IBAN-Mapping aus JWT-Sub → wird in allen Formularen vorausgefüllt

---

## 10. Testdaten dieser Session

### B2B Adressbuch (Einträge aus seed_dev.sql)
| Label | Wallet | Status |
|---|---|---|
| Müller GmbH | 0xA100...0001 | ACTIVE |
| Schmidt AG | 0xA200...0002 | ACTIVE |
| Weitere... | ... | ... |

### Telefonnummern für P2P-Test (Salt: `atruvia-stablecoin-2026`)
`+4915112345678`, `+4917698765432`, `+521551234567`, `+639171234567`, `+2348012345678`

---

## 11. Testabdeckung (Stand 2026-08-19)

**Gesamt: 218 Tests | 218 bestanden | 0 Fehler | LINE: ~82% | BRANCH: ~71% | CLASS: ~95%**

> Testlauf lokal ausgeführt am 2026-08-20 mit OpenJDK 21 + Maven 3.8.7, PostgreSQL 16 (lokal).
> TC-04-Fehler behoben (2026-08-20): Root Cause war fehlender `stablecoin_app` PostgreSQL-User im Setup.

### Neue Test-Klassen (2026-08-19, Sprint 3 Hardening)

| Test-Klasse | TCs | Art | Abdeckungsgewinn |
|---|---|---|---|
| `B2bControllerTest` | 31 | MockMvc `@WebMvcTest` | controller/b2b: 18% → **~88%** |
| `B2cRemittanceServiceTest` | 12 | Mockito Unit | service/b2c: 40% → **~82%** |
| `B2cP2pServiceTest` | 8 | Mockito Unit | B2cP2pService voll abgedeckt |
| `B2cMicropaymentServiceTest` | 15 | Mockito Unit | B2cMicropaymentService voll abgedeckt |
| `ClientContractTest` | 11 | **WireMock 3.6** | client/http: 0% → **~75%** |
| `RateLimitingFilterTest` | 11 | Mockito + Thread-Safety | RateLimitingFilter 100% |
| **Gesamt neu** | **88** | | **+88 TCs gegenüber 130** |

### Abdeckung nach Paket (Schätzung, lokal gemessen)

```
service/compliance:  100%   outbox:              97%
service/b2b:          82%   exception:           86%
service/fx:           83%   entity:              69%
service/inbound:     ~75%   controller/b2b:      88%  (war 18%)
service/b2c:          83%   controller/b2c:      38%
controller/common:   37%   client/http (WM):    75%  (war 0%)
config/ratelimit:   100%   (TokenBucket, Filter, TenantType)
```

### Bugfix TC-04 (2026-08-20)

- **`CrossTenantInterbankenClearanceTest.tc04_rlsIsolation`**: ✅ **Behoben**
  - **Root Cause**: Der PostgreSQL-User `stablecoin_app` fehlte im Setup. Flyway V8 (`ENABLE ROW LEVEL SECURITY + GRANT ... TO stablecoin_app`) setzt voraus, dass `stablecoin_app` vor dem ersten Migrationsrun existiert. Ohne diesen User lief die Anwendung als Table-Owner `stablecoin`, der RLS automatisch bypassed. Dadurch waren alle Transaktionen mandantenübergreifend sichtbar — TC-04 schlug fehl.
  - **Fix**: `stablecoin_app` muss vor dem Flyway-Start existieren. Neu: `testdata/init_postgres.sql` (wird per docker-compose `initdb.d` automatisch ausgeführt). Für lokales Setup ohne Docker: `CREATE USER stablecoin_app WITH PASSWORD 'stablecoin_app_pass';`
  - **V19**: Neues `V19__grant_limit_change_log_to_app_user.sql` — behebt fehlenden Grant für `limit_change_log` (erstellt in V16, von V8-Bulk-Grant nicht erfasst, von V15 nicht nachgepflegt).

Neue Test-Klassen (2026-08-18):
- `MultiTenancyIntegrationTest` (5 TCs): RLS-Isolation, EntityListener, JWT-Tenant-Propagation
- `InboundProcessingTest` (2 TCs): LOW_RISK→SETTLED, HIGH_RISK→FAILED+AML_INBOUND_BLOCK

### Testinfrastruktur
- `AbstractLocalDbTest`: `@SpringBootTest` mit lokaler PostgreSQL (kein Docker) — läuft immer
- `AbstractIntegrationTest`: Testcontainers PostgreSQL (`@Testcontainers(disabledWithoutDocker=true)`)
- `MultiTenancyIntegrationTest`: kein Extend von AbstractLocalDbTest — nutzt direkt `application-dev.yml` (stablecoin_app, RLS aktiv)
- Hinweis: `@Transactional` auf Test-Klasse ist inkompatibel mit `REQUIRES_NEW`-Service-Methoden → **nicht** verwenden, stattdessen manuelles `deleteAllInBatch()` in `@AfterEach`

### Multi-Tenancy Testdaten (2026-08-18)

| Mandant | tenant_id | Dev-Token-URL |
|---|---|---|
| Volksbank Kleinstadt | `tenant-kleine-vb` | `/api/v1/auth/dev-token?customerId=cust-b2b-001&tenant=tenant-kleine-vb` |
| Volksbank Metropole | `tenant-grosse-vb` | `/api/v1/auth/dev-token?customerId=cust-b2b-001&tenant=tenant-grosse-vb` |
| Marktbank AG | `tenant-marktbank` | `/api/v1/auth/dev-token?customerId=cust-b2b-001&tenant=tenant-marktbank` |
| Default (Seed-Daten) | `tenant-default` | `/api/v1/auth/dev-token?customerId=cust-b2b-001` |

Seed-Accounts (V1+V2 Migrationen) haben `tenant_id = 'tenant-default'`. Für Isolation-Tests neue Accounts mit gesetztem `TenantContext` erstellen.

---

## 12. Flyway-Migrationsverlauf

| Version | Datei | Inhalt |
|---|---|---|
| V1 | `V1__init.sql` | 8 Tabellen + Seed-Accounts (B2B, B2C) |
| V2 | `V2__fix_b2b_approval_threshold.sql` | `tx_limit_single` für B2B → 25.000 EUR |
| V3 | `V3__add_institutional_address_book.sql` | `institutional_address_book` Tabelle |
| V4 | `V4__add_hold_id_to_transaction.sql` | `hold_id` Spalte auf `stablecoin_transaction` |
| V5 | `V5__update_transaction_status_enum.sql` | Status-CHECK-Constraint: 7 → 11 Werte |
| V6 | `V6__add_yield_position.sql` | `yield_position` Tabelle + YIELD_REDEEM im type-Constraint |
| V7 | `V7__refactor_audit_log.sql` | AuditLog: 4 neue Spalten (transactionId, fromStatus, toStatus, details), 4 alte entfernt |
| V8 | `V8__enable_row_level_security.sql` | **🆕** tenant-Tabelle (4 Dev-Mandanten) + tenant_id auf 5 Tabellen + RLS-Policies |
| V9 | `V9__add_inbound_status_values.sql` | CHECK-Constraint: 11 → 15 Status-Werte (INCOMING, COMPLIANCE_PENDING, COMPLIANCE_APPROVED, COMPLIANCE_REJECTED) |
| V10 | `V10__enterprise_payment_features.sql` | **🆕** `parent_transaction_id`, INBOUND_RETURN/RETURNED/UNASSIGNED, Sammelkonto-Seed |
| V11 | `V11__buchungskreislauf_spalten.sql` | **🆕** `gross_debit`, `fee_amount`, `ledger_booking_reference`, `slippage_tolerance_bps`, `tax_withheld` |
| V12 | `V12__tenant_settings_und_kill_switch.sql` | **🆕** `tenant_settings` (Preise, Limits, Kill-Switch) + `system_control` + Seed für 4 Tenants |
| V13 | `V13__tax_event.sql` | **🆕** `tax_event` (Audit-Nachweis für AtruviaTaxClient-Meldungen) |
| V14 | `V14__reconciliation_run.sql` | **🆕** `reconciliation_run` (EOD Soll/Haben-Abgleich) |
| V15 | `V15__grant_system_control_to_app_user.sql` | **🆕** GRANT SELECT/INSERT/UPDATE auf neue Tabellen für `stablecoin_app` |
| V16 | `V16__operational_gaps.sql` | **🆕** `phone_hash_algorithm`, `limit_change_log`, `bulk_min_success_rate`, `idempotency_expires_at` |
| V17 | `V17__travel_rule.sql` | **🆕** `beneficiary_*` Felder, `travel_rule_enabled/threshold` in `tenant_settings` |
| V18 | `V18__yield_year_end.sql` | **🆕** `year_end_valuation_eur/tax_event_id/last_valued_year` auf `yield_position`; `tax_event.redeem_tx_id` nullable |

---

## 13. Offene Punkte / Nächste Schritte

> ✅ = erledigt | 🔴 = offen/kritisch | 🟡 = offen/mittel | 🔵 = offen/niedrig

### ✅ Abgeschlossen 2026-08-17
- Balance-Widget Frontend + Ownership-Check (CommonController)
- Live FX-Kurs USDC via ECB (`FxRateService`)
- Whitelist-Erzwingung im Transfer-Flow (MiCA/FATF)
- Nachtlicher Sanctions-Batch (`SanctionsBatchService`)
- Institutionelle Whitelist (`InstitutionalAddressBook` Entity)
- IAM Approver (JWT-Override, Selbst-Genehmigung blockiert)
- State Machine Refactoring (11-Werte TransactionStatus, `transitionTo()`)
- YieldPosition Entity (saubere Trennung Deposit/Redeem/Position)
- AuditLog relationales Schema (V7, kein Regex mehr)
- Ausfallsicherheit (Idempotenz atomar, SUBMIT_TO_BLOCKCHAIN Outbox, CB+Retry)
- Testabdeckung 13% → 62,7% (99 Tests)

### ✅ Abgeschlossen 2026-08-18 (Sprint-Abschluss — 7 Commits)

**Multi-Tenancy & Inbound Processing** (commits `68bb995`, `a607b2e`, `1f06eba`):
- V8: RLS-Policies + 4 Dev-Tenants + `TenantAwareDataSource` + JWT-Claim
- V9: Inbound-Status-Werte + `POST /api/v1/b2b/inbound/webhook`
- AML-Screening (direction="incoming"), FX-Konvertierung, Crash-Recovery

**Sicherheitsschicht** (commit `1cd16f5`):
- ✅ Webhook-HMAC-Signaturprüfung (`X-Circle-Signature`, HTTP 401 AUTH_002)
- ✅ OutboxProcessor RLS-Fix (adminJdbcTemplate für Tenant-Lookup vor TenantContext.set())
- ✅ `settledAt`-Timestamp in InboundProcessingService

**Enterprise Payment Features** (commit `87989d9`, V10):
- ✅ UC-29: `GET /api/v1/b2b/export/camt054` — CAMT.054.001.08 Echtzeit-Avisierung
- ✅ UC-30: Automatische Retouren bei SUSPENDED/BLOCKED Konto → `INBOUND_RETURN` TX
- ✅ UC-31: Sammelkonto (`unassigned-funds`) + Admin-Reassign-Endpunkt

**BaFin Gaps G-01 bis G-07** (commit `a3292e8`, V11–V15):
- ✅ G-01: Gross-Debit-Modell + Storno-Buchung bei FAILED
- ✅ G-02: AtruviaTaxClient Drittsystem-Integration + TaxEvent
- ✅ G-03: TenantSettings — mandantenspezifische Preise/Limits
- ✅ G-04: ReconciliationService EOD @Scheduled(23:00)
- ✅ G-05: HedgeClient Interface + MockHedgeClient
- ✅ G-06: SlippageExceededException + Slippage-Check (100 BPS Default)
- ✅ G-07: KillSwitchFilter + SystemControl + Admin-Endpoints

**BaFin Gaps G-08 bis G-15** (commit `f31336e`, V16–V18):
- ✅ G-08: LimitResolver + limit_change_log Audit-Trail
- ✅ G-09: Idempotenz-Key TTL (30 Tage) + IdempotencyCleanupService
- ✅ G-10: CAMT.029 Rejection-Benachrichtigung
- ✅ G-11: OutboxMonitorService @Scheduled + n8n-Alert
- ✅ G-12: Travel Rule (FATF Rec. 16) Pflichtfelder
- ✅ G-13: Bulk-Payment Mindest-Erfolgsquote (BulkPaymentThresholdException)
- ✅ G-14: PhoneHashService HMAC-SHA256 (PHONE_HMAC_KEY Env-Variable)
- ✅ G-15: YieldYearEndService @Scheduled(31.12. 23:30)

**125 Tests — 0 Failures | Flyway V1–V18 | QA_REVIEW_CHANGES.md vollständig**

**Async Event-Driven Architecture: Kafka + S3** (neuester Commit):
- ✅ `kafka/event/` — 4 JSON-Event-POJOs (TransferStatus, ComplianceScreening, YieldLifecycle, Analytics)
- ✅ `kafka/KafkaEventProducer` Interface + `KafkaTopics` Konstanten
- ✅ `kafka/dev/DevKafkaProducer` — Spring ApplicationEventPublisher (kein Kafka-Broker!)
- ✅ `kafka/dev/DevKafkaConsumer` — @EventListener @Async Consumer-Simulation
- ✅ `kafka/dev/InMemoryEventStore` — GET /api/v1/dev/events für Event-Inspektion
- ✅ `storage/ExportStorageService` Interface + `DevExportStorageService` → /tmp/stablecoin-exports/
- ✅ `storage/dev/DevExportDownloadController` — Presigned-URL-Simulation
- ✅ `analytics/AnalyticsDataProductController` — Data-Mesh GET /api/v1/analytics/**
- ✅ B2bTransferService: Kafka Events bei jedem transitionTo() + settleTransaction()
- ✅ ExportService: `triggerAsyncExport()` → S3-Upload → Presigned URL (15min)
- ✅ `INTEGRATION_TARGET_ARCHITECTURE.md` vollständiges Architektur-Konzept
- ✅ 130 Tests | 0 Failures

**E2E-Test: Mandantenübergreifende Interbanken-Clearance** (vorheriger Commit):
- ✅ `CrossTenantInterbankenClearanceTest.java` — 5 TCs, vollständiger E2E-Kreislauf
- ✅ TC-01: JWT-Token-Generierung (beide Mandanten via `/api/v1/auth/dev-token`)
- ✅ TC-02: Outbound-Transfer Mandant A → SETTLED + grossRevenue-Prüfung
- ✅ TC-03: Inbound-Webhook Mandant B → SETTLED (Circle-Simulation)
- ✅ TC-04: RLS-Isolation mathematisch bewiesen (Mandant A → 404 für Mandant B TX)
- ✅ TC-05: Ertragsformel R = (V×S) + F − C ≈ 17.492 EUR verifiziert
- ✅ `testdata/seed_e2e_interbanken.sql` — korrigierte Testdaten (8 Spec-Fehler dokumentiert)
- ✅ 130 Tests | 0 Failures | 0 Errors

**Dev-Portal implementiert** (vorheriger Commit):
- ✅ `features/dev-portal/dev-portal.component.ts` — interaktive Startseite
- ✅ 5 Playbook-Tabs: B2B Outbound, B2C Retail, Compliance, Exporte, BaFin-Gaps
- ✅ Webhook-Simulator (UC-27) mit LOW/HIGH/UNBEKANNT Szenarien
- ✅ Export-Downloads: CAMT.053, CAMT.054, CAMT.029, DATEV direkt aus UI
- ✅ Tenant-Login-Gate (3 Volksbanken + B2B/B2C Benutzer)
- ✅ System-Health-Widget (Live `/actuator/health`)
- ✅ Architektur-Diagramm (Dual-Rail + RLS Multi-Tenancy)
- ✅ `testdata/seed_ui_playbook.json` — strukturiertes Playbook-Datenmodell

### Offene Punkte (verbleibend)

| Priorität | Beschreibung | Kategorie |
|---|---|---|
| 🔴 Hoch | `PHONE_HMAC_KEY` in Prod-Deployment setzen (kein Default-Fallback in Prod) | Security |
| 🔴 Hoch | `CIRCLE_WEBHOOK_SECRET` in Prod setzen | Security |
| ✅ **Erledigt 2026-08-19** | Rate-Limiting implementiert (Token-Bucket, alle Endpunkte, Tenant-basiert + IP) | Security |
| 🟡 Mittel | `HttpDzBankHedgeClient` implementieren (nur Interface + Mock vorhanden) | Feature |
| 🟡 Mittel | n8n-Alert-Kanal für ReconciliationService verdrahten | Betrieb |
| 🟡 Mittel | Admin-Trigger-Endpunkt für YieldYearEndService | Feature |
| 🟡 Mittel | `client/http` Contract-Tests (WireMock) | QS |
| 🔵 Niedrig | Angular Production Build verifizieren | Deployment |

### Security Test (Penetration-Test) 🔴

| Bereich | Beschreibung | Tool |
|---|---|---|
| JWT | alg=none, schwache Secrets, Token-Replay | jwt_tool, Burp Suite |
| Authorization | IDOR auf /accounts/{iban}, /transactions/{id} | Burp Suite Pro |
| Input Validation | SQLi, XSS, Path-Traversal | OWASP ZAP, SQLMap |
| Rate Limiting | ✅ Implementiert (2026-08-19) — Token-Bucket, 429 RATE_LIMIT_EXCEEDED | k6, Artillery |
| Dependency-CVEs | Spring Boot 3.3.5, Jackson, JJWT | `mvn dependency-check:check` + `NVD_API_KEY` als GitHub Secret |
| mTLS/TLS | Cipher-Suite-Prüfung für Prod | testssl.sh |

### Deployment 🔵
- Angular Production Build: `ng build --configuration production` unverifiziert
- Railway Deploy: Prod-Secrets (Circle/Taurus/Chainalysis API-Keys) eintragen
- mTLS: `SSL_KEYSTORE_PATH` für Railway bereitstellen
- OWASP CI: `NVD_API_KEY` als GitHub Secret (Settings → Secrets → Actions)
