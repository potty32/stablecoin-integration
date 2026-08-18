# Use Cases — Atruvia Stablecoin Integration Platform · Version 3

> **Stand: 2026-08-18 (Async Kafka + S3 Architektur)** | Commits: `68bb995` → aktuell  
> Vollständige Dokumentation aller **34+ Use Cases** (UC-01–UC-31 + G-01–G-15 Gap-Fixes + Dev-Portal).  
> Alle Änderungen aus Session 1–8 sind eingearbeitet.

---

## 🚀 Interaktives Dev-Portal — Einstiegspunkt

```
URL: http://localhost:4200/dev-portal  (Startseite nach Login)
     http://localhost:4200/            (Redirect zu /dev-portal)
```

Das Dev-Portal ermöglicht **interaktives Durchspielen aller Use Cases** ohne Dokumentationsstudium:

| Feature | Beschreibung |
|---|---|
| **System-Health** | Live-Anzeige ob Backend (Spring Boot) erreichbar ist |
| **Architektur-Diagramm** | Dual-Rail (SWIFT vs. Stablecoin) + RLS Multi-Tenancy visuell erklärt |
| **Playbook Tabs** | B2B Outbound · B2C Retail · Compliance & Admin · Exporte · BaFin-Gaps |
| **Testdaten** | Jeder UC zeigt vorbereitete IBAN, Wallet-Adressen, Beträge |
| **Klickfolge** | Schritt-für-Schritt Anleitung für jeden UC |
| **Direkt-Start** | Einloggen + Navigation zur Zielseite per Button |
| **Login-Gate** | Mandanten-Dropdown (3 Volksbanken) + Benutzer-Dropdown (B2B/B2C) |
| **Webhook-Simulator** | UC-27: Inbound-Webhook direkt aus UI triggern (LOW/HIGH/UNBEKANNT) |
| **Export-Downloads** | CAMT.053, CAMT.054, CAMT.029, DATEV-CSV per Button |
| **Sanctions-Trigger** | Nachtbatch manuell auslösen (UC-22) |

### Async Kafka + S3 Architektur (Event-Driven)

```bash
# Dev-Mode: Kafka-Events ohne Broker inspizieren
GET http://localhost:8080/api/v1/dev/events                    # alle Events
GET http://localhost:8080/api/v1/dev/events?topic=stablecoin-transfers
DELETE http://localhost:8080/api/v1/dev/events                 # leeren

# S3-Export asynchron triggern (dev: schreibt nach /tmp/stablecoin-exports/)
POST http://localhost:8080/api/v1/b2b/export/async-trigger?type=camt053
→ { presignedUrl: "http://localhost:8080/api/v1/dev/exports/download?token=...", validForSeconds: 900 }

# Verfügbare Dev-Exporte auflisten
GET http://localhost:8080/api/v1/dev/exports/list

# Data-Mesh Analytics (RLS-isoliert nach Mandant)
GET http://localhost:8080/api/v1/analytics/summary
GET http://localhost:8080/api/v1/analytics/revenue
```

**Architekturdokument:** `INTEGRATION_TARGET_ARCHITECTURE.md`

---

### E2E-Testausführung (Interbanken-Clearance)

```bash
# Vollständiger E2E-Test: Mandantenübergreifender Stablecoin-Transfer
mvn test -Dtest=CrossTenantInterbankenClearanceTest -pl backend

# Testsuite gesamt (130 Tests)
mvn test -pl backend
```

**Test:** `CrossTenantInterbankenClearanceTest` (5 TCs, ~150s)

| TC | Beschreibung | Erwartung |
|---|---|---|
| TC-01 | JWT-Tokens für Mandant A + B | HTTP 200, Tokens mit tenant-Claim |
| TC-02 | Outbound-Transfer Mandant A (10.000 EUR USDC) | HTTP 201, status=SETTLED |
| TC-03 | Circle-Webhook simuliert Eingang Mandant B | HTTP 201, status=SETTLED, USDC=10.000 |
| TC-04 | RLS-Gegencheck (Mandant A sieht Mandant B TX nicht) | HTTP 404 (PostgreSQL RLS) |
| TC-05 | Ertragsformel R = (V×S) + F − C ≈ 17,492 EUR | grossRevenue ≈ 17.49 EUR |

**Seed-Daten:** `testdata/seed_e2e_interbanken.sql`

---

### Schnellstart

```bash
# Terminal 1: Backend starten
cd backend && SPRING_PROFILES_ACTIVE=dev java -jar target/stablecoin-backend-1.0.0.jar

# Terminal 2: Frontend starten  
cd frontend && npx ng serve

# Browser öffnen
open http://localhost:4200
# → Automatisch zu /dev-portal umgeleitet

# Mandant wählen: Volksbank Kleinstadt eG
# Benutzer wählen: Müller GmbH (B2B, Initiator)
# "Als Testnutzer einloggen" klicken
# → Tab "B2B Outbound" → UC-01 → "🚀 Direkt-Start"
```

### Playbook-Szenarien (strukturiert)

Alle Szenarien sind auch als JSON-Datenmodell verfügbar: `testdata/seed_ui_playbook.json`

---

## Übersicht

| # | Bereich | Use Case | Endpunkt |
|---|---|---|---|
| UC-01 | B2B | Transfer initiieren | POST /api/v1/b2b/transfers |
| UC-02 | B2B | Transfers auflisten | GET /api/v1/b2b/transfers |
| UC-03 | B2B | Transfer-Detail abrufen | GET /api/v1/b2b/transfers/{id} |
| UC-04 | B2B | Transfer freigeben (Vier-Augen) | POST /api/v1/b2b/transfers/{id}/approve |
| UC-05 | B2B | Transfer ablehnen | POST /api/v1/b2b/transfers/{id}/reject |
| UC-06 | B2B | FX-Kurs sichern (Rate-Quote) | GET /api/v1/b2b/rate-quote |
| UC-07 | B2B | Adresse whitelisten | POST /api/v1/b2b/address-book |
| UC-08 | B2B | Adressbuch auflisten | GET /api/v1/b2b/address-book |
| UC-09 | B2B | Adresse widerrufen | DELETE /api/v1/b2b/address-book/{id} |
| UC-10 | B2B | Bulk-Payment per CSV | POST /api/v1/b2b/bulk-payments |
| UC-11 | B2B | Export CAMT.053 (ISO 20022) | GET /api/v1/b2b/export/camt053 |
| UC-12 | B2B | Export DATEV-CSV | GET /api/v1/b2b/export/datev |
| UC-13 | B2C | Auslandsüberweisung (Remittance) | POST /api/v1/b2c/remittances |
| UC-14 | B2C | P2P-Zahlung via Telefonnummer | POST /api/v1/b2c/p2p/phone |
| UC-15 | B2C | Telefon-Alias registrieren | POST /api/v1/b2c/p2p/phone/register |
| UC-16 | B2C | Yield-Sparkonto eröffnen | POST /api/v1/b2c/savings/yield/deposit |
| UC-17 | B2C | Yield-Position auflösen | DELETE /api/v1/b2c/savings/yield/{id} |
| UC-18 | B2C | Yield-Position abrufen | GET /api/v1/b2c/savings/yield |
| UC-19 | B2C | Card-Wallet abrufen | GET /api/v1/b2c/card/wallet |
| UC-20 | B2C | Biometrie-Micropayment | POST /api/v1/b2c/micropayments |
| UC-21 | Common | Kontostand abfragen | GET /api/v1/accounts/{iban}/balance |
| UC-22 | Common | Transaktion abrufen | GET /api/v1/transactions/{id} |
| UC-23 | B2B | Nachtlicher Sanctions-Batch | POST /api/v1/b2b/admin/sanctions-scan |
| UC-24 | B2B | Inst. Adresse hinzufügen | POST /api/v1/b2b/institutional-address-book |
| UC-25 | B2B | Inst. Adressen auflisten | GET /api/v1/b2b/institutional-address-book |
| UC-26 | B2B | Inst. Adresse widerrufen | DELETE /api/v1/b2b/institutional-address-book/{id} |

---

## B2B — Unternehmenskunden

---

### UC-01 · Transfer initiieren

**Summary**
Ein Firmenkunde löst eine Outbound-Stablecoin-Zahlung aus (EUR → USDC/EURC auf Polygon).
Der Service prüft Idempotenz, Whitelist-Zugehörigkeit (Kunden-Adressbuch ODER institutionelle Liste),
führt Compliance-Screen und Blockchain-Settlement durch — oder parkt die TX bei Vier-Augen-Pflicht.

**Fachliche Einordnung**
- Kernprozess des "Turbo Rail": klassische SWIFT-Überweisung durch Circle + Taurus auf Blockchain ersetzt
- MiCA-Pflicht: AML-Screening (Chainalysis) vor jedem Settlement
- Whitelist-Pflicht (MiCA/FATF Travel Rule): Zieladresse muss ACTIVE in Kunden-Adressbuch ODER institutioneller Whitelist stehen → sonst 403 NOT_WHITELISTED
- Vier-Augen-Regel greift wenn `amountEur > txLimitSingle` (Seed: 25.000 EUR für B2B)
- Gebühr: 2,50 EUR Flat + 0,15% FX-Spread
- FX-Rate: EURC=1.0 (fix), USDC=ECB-Referenzkurs live (`FxRateService`)
- Rate-Quote optional: Kurs kann 60 Sekunden vorher gesichert werden

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController` | REST-Endpunkt, liest `X-Idempotency-Key` |
| `B2bTransferService` | Orchestrierung (kein eigenes `@Transactional`) |
| `FxRateService` | Basisrate: EURC→1.0, USDC→ECB live |
| `FxRateClient` | Interface: `MockFxRateClient` (dev) / `HttpEcbRateClient` (prod) |
| `AddressBookRepository` | Kunden-Whitelist-Check |
| `InstitutionalAddressBookRepository` | Institutionelle Whitelist-Check (OR-Logik) |
| `ComplianceService` | Chainalysis-Screen mit Circuit Breaker |
| `RevenueService` | Spread + Fee-Berechnung |
| `CoreBankingClient` | Hold + Ledger-Buchung |
| `TaurusCustodyClient` | MPC-Signatur + Blockchain-Submit |
| `CircleWalletClient` | USDC/EURC Transfer initiieren |
| `N8nWebhookClient` | Settlement-Notification (Fire & Forget) |
| `StablecoinTransactionRepository` `ApprovalWorkflowRepository` `RateQuoteRepository` `OutboxMessageRepository` `AuditLogRepository` | Persistenz |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2b/transfers
  │  Header: X-Idempotency-Key
  ▼
B2bController → B2bTransferService.initiate()
  │
  ├── DB: findByIdempotencyKey() → 409 wenn Duplikat
  ├── self.persistInitialTransaction() [REQUIRES_NEW]
  │     ├── AccountRepository.findByIban(sourceIban)
  │     │
  │     ├── Whitelist-Check (OR-Logik):
  │     │     ├── addressBookRepo.findBy...ACTIVE → in Kunden-Whitelist?
  │     │     ├── institutionalAddressBookRepo.findBy...ACTIVE → in institutioneller Whitelist?
  │     │     └── [weder noch] → ComplianceBlockException("NOT_WHITELISTED") → 403
  │     │
  │     ├── FxRateService.getBaseRate(currency)
  │     │     ├── EURC → 1.0
  │     │     └── USDC → FxRateClient.getEurUsdRate() (~1.0823)
  │     ├── effectiveRate = baseRate + baseRate * fxSpread
  │     ├── DB: TX INSERT (PENDING)
  │     ├── DB: OutboxMessage + AuditLog
  │     │
  │     ├─ [> 25.000 EUR] → ApprovalWorkflow INSERT → AWAITING_APPROVAL → ← 201
  │     └─ [≤ 25.000 EUR] → weiter zu executeTransferFlow()
  │
  └── executeTransferFlow()
        ├── ComplianceService.screenAndAssert(wallet) [Circuit Breaker]
        │     └── [HIGH_RISK/0xDEAD...] → BLOCKED
        ├── CoreBankingClient.createHold()
        ├── TaurusClient.signAndSubmit()
        ├── CircleClient.initiateTransfer() → COMPLETE + blockchainHash
        ├── RevenueService.calculate(amount, B2B)
        ├── CoreBankingClient.createLedgerBooking()
        ├── self.settleTransaction() [REQUIRES_NEW] → SETTLED
        └── N8nClient.notifySettlement() [best effort]
```

**Code-Schnipsel**

```java
// Whitelist-Check — OR-Logik: Kunden-Adressbuch ODER institutionelle Whitelist
boolean inCustomerWhitelist = addressBookRepository
    .findByCustomerAccountIdAndWalletAddressAndStatus(
        account.getId(), request.destinationWallet(), AddressStatus.ACTIVE)
    .isPresent();
boolean inInstitutionalWhitelist = institutionalAddressBookRepository
    .findByWalletAddressAndStatus(request.destinationWallet(), InstitutionalAddressStatus.ACTIVE)
    .isPresent();
if (!inCustomerWhitelist && !inInstitutionalWhitelist) {
    throw new ComplianceBlockException(request.destinationWallet(), "NOT_WHITELISTED");
}

// FX-Rate live statt hardcoded
BigDecimal baseRate = fxRateService.getBaseRate(request.currency());
BigDecimal effectiveRate = baseRate.add(baseRate.multiply(fxSpread));
```

**Wichtige Punkte**
- Self-Injection (`@Lazy @Autowired private B2bTransferService self`) für `REQUIRES_NEW`-AOP-Proxy
- Whitelist-Block vor dem ersten `txRepository.save()` → kein TX-Record bei Whitelist-Fehler
- FxRateService: `MockFxRateClient` (dev, 1.0823 fix) / `HttpEcbRateClient` (prod, ECB SDMX-JSON)
- Transactional Outbox: jede Statusänderung schreibt in `outbox_message`

---

### UC-02 · Transfers auflisten

**Summary**
Paginierte Liste aller Transfers des eingeloggten Firmenkunden, optional gefiltert nach Status.

**Fachliche Einordnung**
- Read-only, kein externer API-Call
- JWT-Sub (`customerId`) als Mandanten-Filter
- Default-Sort: `createdAt DESC`

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/transfers?status=SETTLED&page=0&size=20
  ▼
B2bController → B2bTransferService.listTransfers()
  ├── CustomerAccountRepository.findByCustomerId(userId)
  ├── TxRepository.findByCustomerAccountId[AndStatus](pageable)
  ├── Pro TX: ApprovalRepository.findByTransactionId() → requiresApproval-Flag
  └── ← 200 TransferPageResponse {content[], total, pages, page, size}
```

---

### UC-03 · Transfer-Detail abrufen

**Summary**
Einzelne Transaktion per UUID mit vollständiger Status-Timeline. Die Timeline wird direkt
aus dem relationalen `AuditLog`-Schema gelesen — kein Regex-Parsing mehr.

**Fachliche Einordnung**
- `AuditLog` speichert `fromStatus`/`toStatus` als Enum-Spalten (direkt indexierbar)
- `buildTimeline()` ist ein einfacher DB-Query nach `transaction_id`, sortiert nach `timestamp`
- Jeder `TimelineEntry` enthält: `fromStatus`, `toStatus`, `performedBy`, `at`, `details`

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/transfers/{id}
  ▼
B2bController → B2bTransferService.getById()
  ├── TxRepository.findById(id) → 404 wenn nicht gefunden
  ├── ApprovalRepository.findByTransactionId() → requiresApproval-Flag
  ├── buildTimeline(txId)
  │     ├── AuditLogRepository.findByTransactionIdOrderByTimestampAsc(txId)
  │     ├── filter: nur Einträge mit toStatus != null
  │     └── map → TimelineEntry {fromStatus, toStatus, performedBy, at, details}
  └── ← 200 TransactionResponse + timeline[]

Beispiel-Timeline für einen SETTLED-Transfer:
  null → CREATED         | by:cust-b2b-001 | "Transfer initiiert: 500 EUR USDC"
  CREATED → COMPLIANCE_CHECKED | by:cust-b2b-001 | "Statuswechsel: CREATED → COMPLIANCE_CHECKED"
  COMPLIANCE_CHECKED → FUNDS_HELD | by:cust-b2b-001 | "EUR-Hold angelegt: holdId=hold-xxxx"
  FUNDS_HELD → SUBMITTED | by:cust-b2b-001 | "Statuswechsel: FUNDS_HELD → SUBMITTED"
  SUBMITTED → SETTLED    | by:SYSTEM        | "Settlement abgeschlossen: blockchainHash=0xabc..."
```

---

### UC-04 · Transfer freigeben (Vier-Augen)

**Summary**
Ein zweiter Autorisierter gibt einen `AWAITING_APPROVAL`-Transfer frei. `approverId` kommt
aus dem JWT — der Request-Body-Wert wird ignoriert. Selbst-Genehmigung ist technisch blockiert.

**Fachliche Einordnung**
- MiCA-Anforderung: Transaktionen über 25.000 EUR brauchen Dual-Control
- 24h Approval-Fenster: läuft ab → `EXPIRED`
- `approverId` = `auth.getName()` (JWT `sub`) — kein freier String mehr
- Selbst-Genehmigung: `workflow.getInitiatorId().equals(approverId)` → 400 BIZ_001 (außer `dev-mode=true`)

**Sequenzdiagramm**

```
Approver
  │  POST /api/v1/b2b/transfers/{id}/approve
  │  Body: {approverId}  ← wird ignoriert, JWT überschreibt
  ▼
B2bController
  │  new ApproveTransferRequest(auth.getName())  ← JWT-Override
  ▼
B2bTransferService.approve()
  ├── self.commitApproval() [REQUIRES_NEW]
  │     ├── Workflow laden
  │     ├── [status ≠ PENDING_APPROVAL] → 400
  │     ├── [expiresAt < now] → EXPIRED → 400
  │     ├── [!devMode && initiatorId == approverId] → 400 BIZ_001 Selbst-Genehmigung
  │     └── DB: APPROVED, approverId, approvedAt
  └── executeTransferFlow() → SETTLED
```

**Code-Schnipsel**

```java
// Controller: JWT überschreibt Request-Body
return ResponseEntity.ok(transferService.approve(id,
    new ApproveTransferRequest(auth.getName())));

// Service: Selbst-Genehmigung prüfen
if (!devMode && workflow.getInitiatorId().equals(request.approverId())) {
    throw new IllegalStateException("Self-approval not allowed");
}
```

---

### UC-05 · Transfer ablehnen

**Summary**
Zweiter Autorisierter lehnt einen wartenden Transfer ab. Analog UC-04: `approverId` aus JWT,
Selbst-Ablehnung blockiert. Workflow → `REJECTED`, TX → `FAILED`.

**Sequenzdiagramm**

```
Approver
  │  POST /api/v1/b2b/transfers/{id}/reject
  ▼
B2bController → new ApproveTransferRequest(auth.getName())
  ▼
B2bTransferService.reject() [@Transactional]
  ├── Workflow laden
  ├── [status ≠ PENDING_APPROVAL] → 400
  ├── [!devMode && self-reject] → 400 BIZ_001
  ├── DB: workflow.status = REJECTED
  ├── DB: tx.status = FAILED, failureReason = "Rejected by: approverId"
  ├── DB: AuditLog REJECTED
  └── ← 200 TransactionResponse (FAILED)
```

---

### UC-06 · FX-Kurs sichern (Rate-Quote)

**Summary**
Firmenkunde fragt einen verbindlichen EUR→USDC/EURC-Kurs ab, der 60 Sekunden gültig ist.
USDC-Rate kommt live vom ECB-Referenzkurs (~1.0823), nicht mehr hardcoded 1.0.

**Fachliche Einordnung**
- MiCA Art. 23: Kurs muss dem Kunden vor Auftragserteilung mitgeteilt werden
- EURC: Basisrate=1.0 (1:1 mit EUR, keine FX-Konvertierung)
- USDC: Basisrate=ECB EUR/USD-Referenzkurs via `FxRateService`
- Dev-Mock: `MockFxRateClient` → 1.0823 | Prod: `HttpEcbRateClient` → ECB SDMX-JSON live
- Effektivrate = Basisrate + Basisrate × fxSpread (0,15%)

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/rate-quote?amountEur=1000&currency=USDC
  ▼
B2bTransferService.createRateQuote() [@Transactional]
  ├── FxRateService.getBaseRate(USDC) → 1.0823 (ECB)
  ├── rate = 1.0823 + 1.0823 * 0.0015 = 1.08392345
  ├── expiresAt = now + 60s
  ├── DB: RateQuote INSERT (status=ACTIVE)
  └── ← 200 {quoteId, rate=1.08392345, targetAmount="1083.92", spread=0.15%, fee=2.50}
```

**Code-Schnipsel**

```java
BigDecimal baseRate = fxRateService.getBaseRate(currency);
// EURC: 1.0 | USDC: 1.0823 (ECB)
BigDecimal rate = baseRate.add(baseRate.multiply(fxSpread)).setScale(8, RoundingMode.HALF_UP);
```

---

### UC-07 · Adresse whitelisten

**Summary**
Firmenkunde fügt eine Destination-Wallet dem persönlichen Adressbuch hinzu.
Chainalysis prüft die Adresse vorab — nur LOW/MEDIUM-Risk wird akzeptiert (Fail-Closed).

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2b/address-book {walletAddress, label, currency}
  ▼
AddressBookService.addAddress() [@CircuitBreaker(chainalysis)]
  ├── ChainalysisClient.screenAddress()
  ├─ [HIGH_RISK / 0xDEAD...] → 403 COMPLIANCE_001
  ├─ [Chainalysis down] → Fallback → 403 (Fail-Closed!)
  └─ [LOW/MEDIUM]
        ├── DB: AddressBook INSERT (status=ACTIVE, riskScore gespeichert)
        ├── DB: AuditLog ADDRESS_ADDED
        └── ← 201 AddressBookResponse
```

**Wichtige Punkte**
- Circuit Breaker muss `ComplianceBlockException` als `ignore-exception` konfiguriert haben
- Kunden-Adressbuch ist EINE der zwei Whitelist-Quellen für UC-01

---

### UC-08 · Adressbuch auflisten

**Summary**
Liste aller ACTIVE-Einträge des persönlichen Kunden-Adressbuchs (REVOKED gefiltert).

```
Client → AddressBookService.listAddresses() [readOnly]
  → AddressBookRepository.findByCustomerAccountIdAndStatus(ACTIVE)
  ← 200 List<AddressBookResponse>
```

---

### UC-09 · Adresse widerrufen

**Summary**
Soft-Delete einer Whitelist-Adresse: Status → `REVOKED`. Eintrag bleibt in DB (Audit Trail).
Ab sofort kann die Adresse nicht mehr als Transfer-Ziel aus dem Kunden-Adressbuch verwendet werden.

```
Client
  │  DELETE /api/v1/b2b/address-book/{id}
  ▼
AddressBookService.revokeAddress() [@Transactional]
  ├── DB: address.status = REVOKED
  ├── DB: AuditLog ADDRESS_REVOKED
  └── ← 204 No Content
```

---

### UC-10 · Bulk-Payment per CSV

**Summary**
CSV-Upload mit beliebig vielen Transfer-Zeilen. Jede Zeile wird als eigener Transfer (UC-01)
initiiert inkl. Whitelist-Check und Vier-Augen-Logik. Pro-Row-Auswertung im Response.

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2b/bulk-payments (CSV, multipart)
  ▼
BulkPaymentService.process()
  ├── CSV parsen (UTF-8, Header überspringen)
  └── Pro Zeile: destinationWallet, amountEur, currency, reference
        ├── [Validierungsfehler] → ERROR row (kein Transfer)
        └── [OK] → B2bTransferService.initiate() → UC-01 vollständig
              └── row = {status: OK/ERROR, txId}
  ← 200 BulkPaymentResult {total, successful, failed, rows[]}
```

**Wichtige Punkte**
- Kein globaler Batch-Rollback — bereits committed Zeilen bleiben
- Vier-Augen greift pro Zeile wenn > 25.000 EUR

---

### UC-11 · Export CAMT.053 (ISO 20022)

**Summary**
Download aller SETTLED Transaktionen als CAMT.053.001.08-konformes XML.
Blockchain-Hash in `AddtlNtryInf` für On-Chain-Verifizierung.

```
Client
  │  GET /api/v1/b2b/export/camt053?iban=DE89...
  ▼
ExportService.generateCamt053(iban)
  ├── TxRepository.findByAccountIdAndStatus(SETTLED, unpaged)
  └── DOM-XML: BkToCstmrStmt / GrpHdr / Stmt / Acct / Bal / Ntry[]
        └── AddtlNtryInf = blockchainHash
  ← 200 application/xml (Download)
```

---

### UC-12 · Export DATEV-CSV

**Summary**
Download aller SETTLED Transaktionen als DATEV-kompatibles CSV mit Bruttoertrag-Spalte.

```
Client
  │  GET /api/v1/b2b/export/datev?iban=DE89...
  ▼
ExportService.generateDatev(iban)
  ├── TxRepository.findByAccountIdAndStatus(SETTLED, unpaged)
  └── CSV: Datum, Belegnummer, Betrag_EUR, Stablecoin, Waehrung, Hash, Bruttoertrag, Status
  ← 200 text/csv;charset=UTF-8 (Download)
```

---

## B2C — Privatkunden

---

### UC-13 · Auslandsüberweisung (Remittance)

**Summary**
Privatkunde überweist Geld ins Ausland. EUR → USDC über Circle an Gateway-Wallet.
Dem Absender wird der Lokalbetrag (z.B. 182,00 MXN) angezeigt.

**Fachliche Einordnung**
- Kein Blockchain-Begriff in der UI — Nutzer sieht "Auslandsüberweisung"
- Gebühr: 0,50 EUR flat
- Länder-Mapping: MX→MXN (×18.2), PH→PHP, IN→INR, NG→NGN, default→USD
- Chainalysis-Screen: Absender-Wallet wird geprüft (nicht das Ziel-Gateway)

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/remittances {sourceIban, amountEur, recipientName, country}
  ▼
B2cRemittanceService.send() [@Transactional]
  ├── Idempotenz-Check
  ├── ChainalysisClient.screenAddress(account.wallet) → 403 wenn HIGH_RISK
  ├── DB: TX INSERT (REMITTANCE, USDC, PROCESSING, → GATEWAY_WALLET)
  ├── CircleClient.initiateTransfer(→ REMITTANCE_GATEWAY_WALLET)
  ├── RevenueService.calculate(amount, B2C)
  ├── DB: TX UPDATE (SETTLED)
  └── ← 200 {txId, status=SETTLED, fee=0.50, amountLocal="182.00 MXN",
               duration="< 30 Sekunden", trackingCode="ATR-XXXXXXXX"}
```

---

### UC-14 · P2P-Zahlung via Telefonnummer

**Summary**
Privatkunde sendet Geld an eine Telefonnummer. Nummer wird SHA-256+Salt gehasht,
Empfänger-Wallet aus DB geladen, dann direkter Circle-Transfer. Gebühr: 0,00 EUR.

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/p2p/phone {sourceIban, recipientPhone, amountEur}
  ▼
B2cP2pService.sendToPhone() [@Transactional]
  ├── SHA-256("atruvia-stablecoin-2026" + phone) → hash
  ├── PhoneAliasRepository.findByPhoneNumberHash(hash) → 404 wenn nicht registriert
  ├── DB: TX INSERT (P2P, USDC, PROCESSING)
  ├── CircleClient.initiateTransfer(→ recipientAlias.wallet)
  ├── DB: TX UPDATE (SETTLED)
  └── ← 200 TransactionResponse (SETTLED)
```

**Wichtige Punkte**
- Telefonnummer nie im Klartext gespeichert — Privacy-by-Design
- Salt `"atruvia-stablecoin-2026"` muss bei direkten DB-Inserts exakt stimmen

---

### UC-15 · Telefon-Alias registrieren

**Summary**
Privatkunde verknüpft Telefonnummer mit Wallet-Adresse. Nummer wird gehasht gespeichert.

```
Client
  │  POST /api/v1/b2c/p2p/phone/register {sourceIban, phoneNumber, walletAddress}
  ▼
B2cP2pService.registerPhoneAlias() [@Transactional]
  ├── SHA-256("atruvia-stablecoin-2026" + phone) → hash
  ├── DB: PhoneAlias INSERT {hash, walletAddress, account}
  └── ← 201 Created (kein Body)
```

---

### UC-16 · Yield-Sparkonto eröffnen

**Summary**
Privatkunde legt EURC in einem RWA-Money-Market-Fund an. Rendite: 3,5% p.a. täglich compoundiert.
Erzeugt zwei unabhängige Datensätze: einen unveränderlichen Buchungsbeleg (`YIELD_DEPOSIT` TX)
und eine eigenständige Anlagenposition (`YieldPosition` ACTIVE).

**Fachliche Einordnung**
- `YIELD_DEPOSIT` TX: Unveränderlicher Buchungsbeleg. Status bleibt immer `SETTLED`.
- `YieldPosition` (ACTIVE): Eigenständiger Positionslebenszyklus (ACTIVE → CLOSED). Enthält `principal`, `interestRate`, `depositedAt`.
- Keine Mutation der YIELD_DEPOSIT TX bei Auflösung — BaFin/IT-Audit-konform.

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/savings/yield/deposit {sourceIban, amountEur}
  ▼
B2cYieldService.deposit() [@Transactional]
  ├── Idempotenz-Check
  ├── DB: StablecoinTransaction INSERT (YIELD_DEPOSIT, EURC, SETTLED, → RWA_FUND_WALLET)
  ├── DB: YieldPosition INSERT (ACTIVE, principal=amountEur, interestRate=0.035, depositedAt=now)
  │         depositTransactionId = savedTx.getId()
  ├── DB: AuditLog YIELD_DEPOSIT_CREATED (entityType="YieldPosition")
  └── ← 200 YieldPositionResponse {positionId, amountEur, currentValueEur, status="ACTIVE"}
```

**Code-Schnipsel**

```java
// Zinseszins täglich (DECIMAL64 für Präzision bei langen Laufzeiten)
BigDecimal dailyRate = ANNUAL_RATE_DECIMAL.divide(DAYS_PER_YEAR, MathContext.DECIMAL64);
BigDecimal factor = BigDecimal.ONE.add(dailyRate, MathContext.DECIMAL64)
    .pow((int) days, MathContext.DECIMAL64);
BigDecimal currentValue = principal.multiply(factor, MathContext.DECIMAL64);
```

**Wichtige Punkte**
- `positionId` in der Response = `YieldPosition.id` (nicht die TX-ID)
- Die YIELD_DEPOSIT TX bleibt für immer `SETTLED` — kein Zustandswechsel je

---

### UC-17 · Yield-Position auflösen (Redeem)

**Summary**
Privatkunde löst eine aktive Anlageposition auf. Es wird eine **eigenständige YIELD_REDEEM Transaktion**
erzeugt (Status: `REDEEMED`, Betrag: Anlage + Zinsen). Die `YieldPosition` wechselt auf `CLOSED`.
Die originale `YIELD_DEPOSIT` TX bleibt unberührt.

**Fachliche Einordnung**
- `YIELD_REDEEM` TX: Auszahlungsbeleg (Anlage + aufgelaufene Zinsen). Status: `REDEEMED`.
- `YieldPosition`: wechselt ACTIVE → CLOSED, `closedAt` wird gesetzt.
- Keine Mutation der Einzahlungs-TX (BaFin/IT-Audit: saubere Trennung der Buchungsvorgänge).
- Endpoint: `DELETE /api/v1/b2c/savings/yield/{positionId}` — `{positionId}` = `YieldPosition.id`

**Sequenzdiagramm**

```
Client
  │  DELETE /api/v1/b2c/savings/yield/{positionId}
  ▼
B2cYieldService.redeem(positionId) [@Transactional]
  ├── YieldPositionRepository.findById(positionId)  → 404 wenn nicht gefunden
  ├── [status ≠ ACTIVE] → 400 IllegalStateException
  ├── days = ChronoUnit.DAYS.between(position.depositedAt, now)
  ├── currentValue = principal × (1 + 0.035/365)^days  [DECIMAL64]
  ├── accrued = currentValue − principal
  │
  ├── DB: StablecoinTransaction INSERT (YIELD_REDEEM, EURC, REDEEMED,
  │         amountFiat=currentValue, ← RWA_FUND_WALLET → customer.wallet)
  │
  ├── DB: YieldPosition UPDATE (status=CLOSED, closedAt=now)
  │
  ├── DB: AuditLog YIELD_REDEEMED (ACTIVE → CLOSED, accruedYield, redeemTxId)
  └── ← 204 No Content
```

---

### UC-18 · Yield-Position abrufen

**Summary**
Gibt die aktive `YieldPosition` mit live berechneten Zinsen zurück.
Die Abfrage läuft direkt auf `YieldPosition` (nicht auf `StablecoinTransaction`) —
kein Hack mit FAILED/REDEEMED-Status-Filtern auf Transaktionsebene.

**Fachliche Einordnung**
- Abfrage: `YieldPositionRepository.findByCustomerAccountIdAndStatus(ACTIVE)` → 404 wenn keine
- `CLOSED`-Positionen werden automatisch ausgeschlossen (eigenes Feld, kein TX-Status-Hack)
- `positionId` im Response verweist direkt auf `YieldPosition.id`

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2c/savings/yield
  ▼
B2cYieldService.getPosition() [readOnly]
  ├── AccountRepository.findByCustomerId(auth.getName())
  ├── YieldPositionRepository.findByCustomerAccountIdAndStatus(ACTIVE)
  │     └── [nicht gefunden] → 404 NoSuchElementException
  ├── days = ChronoUnit.DAYS.between(position.depositedAt, now)
  ├── currentValue = principal × (1 + 0.035/365)^days  [live, kein Cache]
  └── ← 200 YieldPositionResponse {positionId, amountEur, currentValueEur, status="ACTIVE"}
```

---

### UC-19 · Card-Wallet abrufen

**Summary**
USDC + EURC-Guthaben des persönlichen Wallets. Basis für das Karten-Widget der B2C-UI.

```
Client → B2cMicropaymentService.getCardWallet() [readOnly]
  ├── CircleClient.getWalletBalance(account.walletAddress)
  └── ← 200 CardWalletResponse {walletAddress, usdc, eurc}
```

---

### UC-20 · Biometrie-Micropayment

**Summary**
Kleinstbetragszahlung (max. 10 EUR) per biometrischer Bestätigung. Merchant-Wallet
deterministisch aus Merchant-ID abgeleitet. Gebühr: 0,10 EUR.

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/micropayments {biometricToken, amountEur, merchantId, contentId}
  ▼
B2cMicropaymentService.pay() [@Transactional]
  ├── [biometricToken.length < 10] → 400
  ├── [amountEur > 10 EUR] → 400
  ├── merchantWallet = "0xMerchantL2Wallet" + sanitize(merchantId).pad(22)
  ├── CircleClient.initiateTransfer(→ merchantWallet)
  ├── DB: TX SETTLED (fee=0.10 EUR)
  └── ← 200 TransactionResponse (SETTLED)
```

---

## Common — Domänenübergreifend

---

### UC-21 · Kontostand abfragen

**Summary**
Gibt EUR-Guthaben (Core Banking) und USDC/EURC-Guthaben (Circle) zurück.
Ownership-Check: Nur der Inhaber der IBAN darf seinen eigenen Kontostand abrufen.
Frontend: Balance-Widget in `TransferListComponent` zeigt die Werte on-load.

**Fachliche Einordnung**
- Ownership-Check: `auth.getName()` → `findByCustomerId()` → `account.getIban().equals(iban)` → sonst 403 AUTH_001
- Frontend: `ngOnInit()` ruft `txService.getAccountBalance(iban)` auf und rendert Widget
- Business-Logik im Controller (Ausreißer in Schichtenarchitektur — kein dedizierter Service)

**Sequenzdiagramm**

```
Client (oder Frontend-Widget ngOnInit)
  │  GET /api/v1/accounts/{iban}/balance
  ▼
CommonController.getBalance()
  ├── AccountRepository.findByCustomerId(auth.getName())
  ├── [account.getIban() ≠ iban] → 403 AUTH_001 AccessDeniedException
  ├── CoreBankingClient.getAccountBalance(iban) → EUR-Betrag
  ├── CircleClient.getWalletBalance("BANK_MASTER_WALLET_ID")
  │     → Map<currency, amount> {USDC: "...", EURC: "..."}
  └── ← 200 AccountBalanceResponse {iban, balanceEur, stablecoinBalances}
```

**Code-Schnipsel**

```java
// Ownership-Check
CustomerAccount requestingAccount = accountRepository.findByCustomerId(auth.getName())
    .orElseThrow(() -> new NoSuchElementException("No account for user: " + auth.getName()));
if (!requestingAccount.getIban().equals(iban)) {
    throw new AccessDeniedException("Access denied to account: " + iban);
}
```

**Frontend-Widget (TransferListComponent)**

```typescript
// ngOnInit: Balance laden
const iban = this.auth.getIban();
if (iban) {
  this.txService.getAccountBalance(iban).subscribe({
    next: b => this.balance = b,
    error: () => {}
  });
}
```

```html
<!-- Template: Balance-Widget über Filter-Row -->
@if (balance) {
  <div style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;
              padding:0.875rem 1rem;margin-bottom:1rem;display:flex;gap:2rem">
    <div>
      <div style="font-size:0.7rem;color:#64748b;text-transform:uppercase">Verfügbares Guthaben</div>
      <div style="font-size:1.25rem;font-weight:700">{{ balance.balanceEur | number:'1.2-2' }} EUR</div>
    </div>
    @for (entry of balance.stablecoinBalances | keyvalue; track entry.key) {
      <div>
        <div style="font-size:0.7rem;color:#64748b;text-transform:uppercase">{{ entry.key }}</div>
        <div style="font-size:1.25rem;font-weight:700;color:#2563eb">{{ entry.value }}</div>
      </div>
    }
  </div>
}
```

---

### UC-22 · Transaktion abrufen (Cross-Domain)

**Summary**
Einzelne Transaktion per UUID abrufbar — B2B und B2C. Inkl. Status-Timeline.
Ownership-Check: Nur der Inhaber der TX darf sie abrufen.

**Fachliche Einordnung**
- Ownership-Check: `tx.getCustomerAccount().getId().equals(requestingAccount.getId())` → sonst 403 AUTH_001
- `requiresApproval`-Flag aus `ApprovalWorkflow`

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/transactions/{id}
  ▼
CommonController.getTransaction()
  ├── TxRepository.findById(id) → 404 wenn nicht gefunden
  ├── AccountRepository.findByCustomerId(auth.getName())
  ├── [tx.customerAccount.id ≠ requestingAccount.id] → 403 AUTH_001
  ├── ApprovalRepository.findByTransactionId() → requiresApproval-Flag
  ├── buildTimeline(id)
  │     └── AuditLogRepository.findByTransactionIdOrderByTimestampAsc(id) → direkte Spalten
  └── ← 200 TransactionResponse + timeline[]
```

---

### UC-23 · Nachtlicher Sanctions-Batch

**Summary**
Täglich 02:00 Uhr scannt `SanctionsBatchService` alle ACTIVE-Adressbucheinträge gegen
Chainalysis. HIGH_RISK-Adressen → REVOKED + AuditLog + n8n-Benachrichtigung.
Manuell auslösbar via `POST /api/v1/b2b/admin/sanctions-scan`.

**Fachliche Einordnung**
- FATF/MiCA: Tägliche Aktualisierung der Sanktionslisten (OFAC SDN, EU-Konsolidierte)
- Fail-safe: Fehler bei einer Adresse bricht nicht den Batch ab
- n8n-Benachrichtigung best effort (Netzwerkfehler werden geloggt, nicht propagiert)
- AuditLog: `userId = "SYSTEM"` (kein authentifizierter User im Scheduler)

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `SanctionsBatchService` | `@Scheduled(cron = "0 0 2 * * ?")` |
| `AddressBookRepository.findByStatus(ACTIVE)` | Alle aktiven Einträge |
| `ChainalysisClient` | Screening pro Adresse |
| `AuditLogRepository` | INSERT `SANCTIONS_BATCH_REVOKED` |
| `N8nWebhookClient.notifyAddressRevoked()` | Kundenmeldung (best effort) |
| `B2bController.triggerSanctionsScan()` | Admin-Trigger |

**Sequenzdiagramm**

```
Scheduler (02:00) ODER Admin POST /admin/sanctions-scan
  ▼
SanctionsBatchService.runNightlySanctionsScan()
  ├── AddressBookRepository.findByStatus(ACTIVE) → alle aktiven Einträge
  └── Pro AddressBook-Eintrag:
        ├── ChainalysisClient.screenAddress(wallet, currency, POLYGON)
        ├─ [approved=true] → überspringen
        └─ [approved=false]
              ├── DB: address.status = REVOKED
              ├── DB: AuditLog SANCTIONS_BATCH_REVOKED (userId="SYSTEM")
              └── N8nClient.notifyAddressRevoked() [best effort]
```

**Code-Schnipsel**

```java
@Scheduled(cron = "0 0 2 * * ?")
@Transactional
public void runNightlySanctionsScan() {
    List<AddressBook> active = addressBookRepository.findByStatus(AddressStatus.ACTIVE);
    int revoked = 0;
    for (AddressBook address : active) {
        try { if (screenAndRevokeIfHighRisk(address)) revoked++; }
        catch (Exception e) { log.error("[SANCTIONS-BATCH] Error: {}", e.getMessage()); }
    }
    log.info("[SANCTIONS-BATCH] Done. revoked={}/{}", revoked, active.size());
}
```

**Wichtige Punkte**
- `@EnableScheduling` war bereits aktiv — kein neues Setup nötig
- Admin-Endpunkt ohne IAM — in Prod ROLE_ADMIN erforderlich
- Direkter Zusammenhang mit UC-01: Revozierte Adressen schlagen beim nächsten Transfer fehl

---

### UC-24 · Institutionelle Adresse hinzufügen

**Summary**
Bank-Administrator fügt eine regulierte Gegenpartei (Coinbase Custody, Kraken etc.) zur
bank-weiten institutionellen Whitelist hinzu. Chainalysis-Screen ist Pflicht (Fail-Closed).

**Fachliche Einordnung**
- Neue DB-Tabelle `institutional_address_book` (V3 Migration) — KEIN `customer_account_id` FK
- Bank-weit: gilt für alle Kunden ohne individuelle Prüfung
- Auswirkung auf UC-01: Transfer an institutionelle Adresse → SETTLED auch ohne Kunden-Adressbuch-Eintrag
- `created_by` = JWT-User → Nachvollziehbarkeit wer die Adresse hinzugefügt hat
- Eindeutigkeit: `UNIQUE(wallet_address, currency)` — gleiche Adresse pro Währung nur einmal

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.addInstitutionalAddress()` | POST /institutional-address-book |
| `InstitutionalAddressBookService.addAddress()` | Chainalysis-Screen + Persist |
| `InstitutionalAddressBook` | Entity (kein CustomerAccount FK) |
| `InstitutionalAddressBookRepository` | `findByWalletAddressAndStatus()` |
| `ChainalysisClient` | Screening |
| `AuditLogRepository` | `INST_ADDRESS_ADDED` |

**Sequenzdiagramm**

```
Admin
  │  POST /api/v1/b2b/institutional-address-book
  │  Body: {label, walletAddress, currency}
  ▼
InstitutionalAddressBookService.addAddress() [@CircuitBreaker(chainalysis)]
  ├── ChainalysisClient.screenAddress(wallet, currency, POLYGON)
  ├─ [!approved / HIGH_RISK] → 403 COMPLIANCE_001
  ├─ [Chainalysis down] → Fallback: 403 (Fail-Closed)
  └─ [approved]
        ├── DB: institutional_address_book INSERT (status=ACTIVE, createdBy=userId)
        ├── DB: AuditLog INST_ADDRESS_ADDED
        └── ← 201 InstitutionalAddressBookResponse
```

**Code-Schnipsel**

```java
// Entity: kein customer_account_id FK
@Entity
@Table(name = "institutional_address_book")
public class InstitutionalAddressBook {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String label;
    private String walletAddress;
    // kein: @ManyToOne private CustomerAccount customerAccount;
    @Enumerated(EnumType.STRING) private StablecoinCurrency currency;
    @Enumerated(EnumType.STRING) private InstitutionalAddressStatus status;
    private String createdBy;
    // ...
}
```

---

### UC-25 · Institutionelle Adressen auflisten

**Summary**
Liste aller ACTIVE-Einträge der bank-weiten institutionellen Whitelist.
Keine Kunden-Filterung — Einträge gelten bank-weit.

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/institutional-address-book
  ▼
InstitutionalAddressBookService.listAddresses() [readOnly]
  ├── InstitutionalAddressBookRepository.findByStatus(ACTIVE)
  └── ← 200 List<InstitutionalAddressBookResponse>
       (REVOKED-Einträge gefiltert, bleiben in DB für Audit)
```

---

### UC-26 · Institutionelle Adresse widerrufen

**Summary**
Soft-Delete einer institutionellen Whitelist-Adresse: Status → `REVOKED`.
Ab sofort können keine Transfers mehr an diese Adresse initiiert werden
(sofern sie nicht im Kunden-Adressbuch steht).

**Sequenzdiagramm**

```
Admin
  │  DELETE /api/v1/b2b/institutional-address-book/{id}
  ▼
InstitutionalAddressBookService.revokeAddress() [@Transactional]
  ├── InstitutionalAddressBookRepository.findById(id) → 404 wenn nicht gefunden
  ├── DB: address.status = REVOKED (Soft-Delete)
  ├── DB: AuditLog INST_ADDRESS_REVOKED
  └── ← 204 No Content
```

**Wichtige Punkte**
- Sofortige Wirkung auf UC-01: Nächster Transfer an diese Adresse schlägt mit 403 fehl
- Kein physisches Löschen — AuditLog bleibt vollständig
- Verknüpfung mit UC-23: Sanctions-Batch kann Einträge ebenfalls automatisch revozieren

---

## Zusammenfassung — Bemerkenswerte Querschnittsthemen

### 1. Self-Injection für REQUIRES_NEW (UC-01, UC-04)

```java
@Lazy @Autowired private B2bTransferService self;
```

Spring-AOP-Proxy notwendig für `REQUIRES_NEW`-Transaktionen. Direktes `this.` würde den Proxy umgehen — Status-Updates nach Fehlern (BLOCKED, FAILED) würden nicht committed werden.

### 2. Transactional Outbox Pattern

Jede Statusänderung schreibt in `outbox_message` — Grundlage für zuverlässige Event-Delivery ohne direkten Kafka/RabbitMQ-Call.

### 3. AuditLog als Append-only Event-Store

`audit_log` ist INSERT-only. Neues relationales Schema (V7-Migration): `transactionId` (indexierter FK), `fromStatus`/`toStatus` (Enum-Spalten), `details` (Klartext). Status-Timeline (UC-03, UC-22) wird direkt per `findByTransactionIdOrderByTimestampAsc()` geladen — kein Regex-Parsing mehr. Performance: Index auf `transaction_id` ermöglicht effizienten Lookup ohne Full-Table-Scan.

### 4. Doppelte Whitelist-Architektur (UC-01, UC-07, UC-24)

Kunden-Adressbuch (per-customer, mit Chainalysis-Screen) ODER institutionelle Whitelist (bank-weit, ebenfalls gescreent). OR-Verknüpfung in `persistInitialTransaction()`.

### 5. Circuit Breaker Fail-Closed (UC-07, UC-24, UC-01)

Chainalysis-Ausfall → Adresse wird blockiert, nicht durchgelassen. `ComplianceBlockException` muss als `ignore-exception` konfiguriert sein, sonst öffnet der Breaker nach jeder HIGH_RISK-Ablehnung.

### 6. Live FX-Rate via ECB (UC-01, UC-06)

`FxRateService` mit Interface-Pattern: `MockFxRateClient` (dev, 1.0823 fix) / `HttpEcbRateClient` (prod, ECB SDMX-JSON). EURC=1.0 (fix), USDC=live. `BASE_RATE`-Hardcode vollständig entfernt.

### 7. Ownership-Check Pattern (UC-21, UC-22)

```java
CustomerAccount requestingAccount = accountRepository.findByCustomerId(auth.getName())
    .orElseThrow(...);
// Für Balance:
if (!requestingAccount.getIban().equals(iban)) throw new AccessDeniedException(...);
// Für Transaction:
if (!tx.getCustomerAccount().getId().equals(requestingAccount.getId())) throw new AccessDeniedException(...);
```

`AccessDeniedException` (Spring Security) → `GlobalExceptionHandler` → 403 AUTH_001.

### 8. IAM Approver — JWT-Override (UC-04, UC-05)

```java
// Controller überschreibt Request-Body-Wert mit JWT-Identity
return ResponseEntity.ok(transferService.approve(id,
    new ApproveTransferRequest(auth.getName())));
```

Selbst-Genehmigung: `!devMode && initiatorId.equals(approverId)` → 400 BIZ_001.

### 9. REDEEMED als eigener terminaler Status (UC-17)

`TransactionStatus.REDEEMED` ist der regulatorisch korrekte Endzustand nach erfolgreicher
Yield-Auflösung. `FAILED` bleibt ausschließlich echten Systemfehlern vorbehalten (BaFin/IT-Audit).
`getPosition()` sucht nur nach `SETTLED` — REDEEMED-Positionen verschwinden damit automatisch.
Die ursprüngliche Verwendung von `FAILED` als Redeem-Marker wurde im Zuge des State-Machine-Refactorings
(Commit `refactor(state-machine)`) bereinigt.

### 10. Integrationstests (Testcontainers)

5 Tests in `src/test/` mit `@Testcontainers(disabledWithoutDocker = true)`:
TC1 Happy-Path | TC2 Whitelist-Block (kein TX) | TC3 Vier-Augen | TC4 Compliance-Block | TC5 Ownership-403.
In Kasm-Umgebung ohne Docker: automatisch geskippt (kein Fehler).

---

---

## UC-29 · CAMT.054 Echtzeit-Avisierung

**Akteur:** ERP-System (SAP) des Firmenkunden  
**Endpunkt:** `GET /api/v1/b2b/export/camt054?iban=...`  
**Format:** ISO 20022 CAMT.054.001.08 (Bank-to-Customer Debit Credit Notification)  
**Filter:** `type=INBOUND`, `status=SETTLED`  
**BkTxCd:** PMNT/RCDT/ESCT (Received Credit Transfer)  
**Testfall:** `EnterprisePaymentFeaturesTest.camt054_settledInboundTxs_generatesValidXmlWithCrdtEntries`

---

## UC-30 · Automatische Retouren (Inbound R-Transaktionen)

**Trigger:** `status=SUSPENDED` oder `status=BLOCKED` des Empfängerkontos nach AML-Screening

**Flow:**
```
Webhook → INCOMING → COMPLIANCE_PENDING → AML OK
                                         → Konto-Status SUSPENDED/BLOCKED?
                                              JA: Original-TX → FAILED (KONTO_INAKTIV)
                                                  INBOUND_RETURN-TX → CREATED → RETURNED
                                              NEIN: weiter normal → SETTLED
```

**Neue Felder:**
- `stablecoin_transaction.parent_transaction_id` — Verknüpfung Retoure ↔ Original (V10)
- `TransactionType.INBOUND_RETURN`
- `TransactionStatus.RETURNED` (terminal)

**Wichtige Impl.-Details:**
- `initiateInboundReturn()` ist `@Transactional(REQUIRES_NEW)` → T3
- Inner `transitionToFailed(originalTxId)` startet T4 (REQUIRES_NEW) — **findet TX, da T2 committed hat**
- `returnTx.setStatus(RETURNED)` direkt in T3 (nicht via `transferService.transitionTo()` — würde T4' benötigen, die T3-Daten nicht sieht)
- `circleWalletClient.initiateTransfer()` → Circle-Rücküberweisung an `senderWallet`

**Testfall:** `EnterprisePaymentFeaturesTest.inboundReturn_suspendedAccount_createsReturnTransaction`

---

## UC-31 · Sammelkonto-Prinzip (Unzuordenbare Geldeingänge)

**Trigger:** Webhook mit Wallet-Adresse, die keinem `customer_account` zugeordnet ist

**Flow:**
```
Webhook → adminJdbcTemplate(BYPASSRLS): wallet → null (keine Match)
        → processUnassignedInbound()
        → TX auf Sammelkonto (customer_id='unassigned-funds', tenant_id='tenant-default')
        → status=UNASSIGNED

Später: Admin prüft manuell
        → POST /api/v1/b2b/admin/reassign-transaction {transactionId, targetIban}
        → ReassignTransactionService: UPDATE stablecoin_transaction (cross-tenant via adminJdbc)
        → CoreBanking.createLedgerBooking() → Gutschrift auf Zielkonto
        → status=SETTLED
```

**Neue Felder:** `TransactionStatus.UNASSIGNED`, V10-Seed: Sammelkonto

**Testfälle:** `EnterprisePaymentFeaturesTest.unassignedInbound_unknownWallet_parkedOnCollectionAccount` + `reassignTransaction_unassignedTx_settlesOnTargetAccount`

---

## State Machine — Transaktionslebenszyklus (aktualisiert V10)

### Erlaubte Zustandsübergänge (18-Werte-Enum)

```
── Outbound-Pfad ────────────────────────────────────────────
CREATED           → PENDING_APPROVAL, COMPLIANCE_CHECKED, INCOMING, FAILED, RETURNED
PENDING_APPROVAL  → APPROVED, REJECTED, EXPIRED, FAILED
APPROVED          → COMPLIANCE_CHECKED, FAILED
COMPLIANCE_CHECKED→ FUNDS_HELD, FAILED
FUNDS_HELD        → SUBMITTED, FAILED  ← FAILED: Auto-Hold-Release + Storno-Buchung (G-01)
SUBMITTED         → SETTLED, FAILED    ← FAILED: Auto-Hold-Release + Storno-Buchung (G-01)
SETTLED           → REDEEMED, FAILED

── Inbound-Pfad ─────────────────────────────────────────────
INCOMING          → COMPLIANCE_PENDING, FAILED
COMPLIANCE_PENDING→ COMPLIANCE_APPROVED, COMPLIANCE_REJECTED, FAILED
COMPLIANCE_APPROVED→SETTLED, FAILED

── Enterprise (V10) ─────────────────────────────────────────
UNASSIGNED        → SETTLED, FAILED         (nach Admin-Reassign)

── Terminal (keine weiteren Übergänge) ──────────────────────
REDEEMED, REJECTED, EXPIRED, FAILED, COMPLIANCE_REJECTED, RETURNED
```

### Status-Semantik (vollständig, 18 Werte)

| Status | Pfad | Bedeutung | Terminal |
|---|---|---|---|
| `CREATED` | Out/In | Transaktion initialisiert | Nein |
| `PENDING_APPROVAL` | Out | Vier-Augen-Freigabe ausstehend | Nein |
| `APPROVED` | Out | Zweitfreigabe erteilt | Nein |
| `REJECTED` | Out | Zweitfreigabe abgelehnt | **Ja** |
| `EXPIRED` | Out | Freigabefrist abgelaufen | **Ja** |
| `COMPLIANCE_CHECKED` | Out | AML/Whitelist erfolgreich | Nein |
| `FUNDS_HELD` | Out | EUR im CoreBanking gesperrt; G-01: Storno bei FAILED | Nein |
| `SUBMITTED` | Out | An Circle/Blockchain übergeben; G-01: Storno bei FAILED | Nein |
| `SETTLED` | Out/In | Erfolgreich verbucht | Nein* |
| `REDEEMED` | Yield | Yield-Anlage aufgelöst | **Ja** |
| `FAILED` | Out/In | Technischer/fachlicher Abbruch | **Ja** |
| `INCOMING` | In | Blockchain-Eingang registriert | Nein |
| `COMPLIANCE_PENDING` | In | Post-Receive AML läuft | Nein |
| `COMPLIANCE_APPROVED` | In | AML erfolgreich → Gutschrift folgt | Nein |
| `COMPLIANCE_REJECTED` | In | AML-Verdacht, Gelder blockiert | **Ja** |
| `UNASSIGNED` | In | Wallet unbekannt → Sammelkonto (UC-31) | Nein** |
| `RETURNED` | In | Inbound-Retoure abgeschlossen (UC-30) | **Ja** |

\* SETTLED → REDEEMED erlaubt (Yield-Redeem-Pfad)  
\** UNASSIGNED → SETTLED nach Admin-Reassign (UC-31)

### State Machine Implementierung

```java
// B2bTransferService.java — ALLOWED-Map (aktueller Stand V10)
private static final Map<TransactionStatus, EnumSet<TransactionStatus>> ALLOWED =
    Map.ofEntries(
        // Outbound
        entry(CREATED,            EnumSet.of(PENDING_APPROVAL, COMPLIANCE_CHECKED, INCOMING, FAILED, RETURNED)),
        entry(PENDING_APPROVAL,   EnumSet.of(APPROVED, REJECTED, EXPIRED, FAILED)),
        entry(APPROVED,           EnumSet.of(COMPLIANCE_CHECKED, FAILED)),
        entry(COMPLIANCE_CHECKED, EnumSet.of(FUNDS_HELD, FAILED)),
        entry(FUNDS_HELD,         EnumSet.of(SUBMITTED, FAILED)),
        entry(SUBMITTED,          EnumSet.of(SETTLED, FAILED)),
        entry(SETTLED,            EnumSet.of(REDEEMED, FAILED)),
        // Inbound
        entry(INCOMING,           EnumSet.of(COMPLIANCE_PENDING, FAILED)),
        entry(COMPLIANCE_PENDING, EnumSet.of(COMPLIANCE_APPROVED, COMPLIANCE_REJECTED, FAILED)),
        entry(COMPLIANCE_APPROVED,EnumSet.of(SETTLED, FAILED)),
        entry(COMPLIANCE_REJECTED,EnumSet.of(FAILED)),
        // Enterprise
        entry(UNASSIGNED,         EnumSet.of(SETTLED, FAILED))
    );

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void transitionTo(UUID txId, TransactionStatus targetStatus, String userId) {
    StablecoinTransaction tx = txRepository.findByIdWithLock(txId).orElseThrow();
    TransactionStatus current = tx.getStatus();

    // Validierung: ungültige Übergänge werden mit IllegalStateException blockiert
    EnumSet<TransactionStatus> allowed = ALLOWED.getOrDefault(current, EnumSet.noneOf(TransactionStatus.class));
    if (!allowed.contains(targetStatus)) {
        throw new IllegalStateException(
            String.format("Ungültiger Statusübergang: %s → %s (erlaubt: %s)", current, targetStatus, allowed));
    }

    // Auto-Hold-Release: bei FAILED aus FUNDS_HELD oder SUBMITTED
    if (targetStatus == FAILED && EnumSet.of(FUNDS_HELD, SUBMITTED).contains(current) && tx.getHoldId() != null) {
        coreBankingClient.releaseHold(tx.getHoldId());
    }

    tx.setStatus(targetStatus);
    txRepository.save(tx);
    saveAuditLog(...);
}
```

### Regulatorische Begründung (BaFin / IT-Audit)

- **FAILED ≠ REDEEMED**: FAILED bedeutet ausschließlich technischer oder fachlicher Abbruch.
  REDEEMED ist ein erfolgreicher Geschäftsvorfall. Prüfer können anhand des Status allein
  erkennen was passiert ist — ohne Kommentare oder Sonderfälle.
- **REJECTED ≠ FAILED**: Manuelle Ablehnung durch Mensch ist kein Systemfehler.
  Wichtig für KPI-Auswertungen (Ablehnungsquote ≠ Fehlerquote).
- **Ungültige Übergänge**: Laufzeit-Exception verhindert stille Datenverfälschung.
  SETTLED → PENDING ist unmöglich.
- **Auto-Hold-Release**: Systeminvariante garantiert dass nach jedem FAILED-Übergang aus
  FUNDS_HELD oder SUBMITTED der EUR-Hold automatisch freigegeben wird.
  Keine manuelle Disposition-Bereinigung nach Systemfehler nötig.

---

---

## Ausfallsicherheits-Architektur (Reliability Patterns)

### 1. Atomare Idempotenz (Race-Condition-frei)

```
Client A ──┐
           ├── POST /transfers (X-Idempotency-Key: abc)
Client B ──┘

persistInitialTransaction() [@Transactional]
  ├── findByIdempotencyKey(abc)          ← Check INSIDE @Transactional
  ├── [gefunden] → IdempotencyConflictException → HTTP 409
  └── [nicht gefunden] → TX INSERT
       DB-UNIQUE-Constraint (idempotency_key) fängt Race-Conditions ab
```

- Check und Insert laufen in **einer** `@Transactional` — keine Race Condition möglich
- DB `UNIQUE`-Constraint ist die zweite Sicherheitslinie → `DataIntegrityViolationException` → 409

### 2. Crash-Recovery via Transactional Outbox (SUBMIT_TO_BLOCKCHAIN)

```
FUNDS_HELD committed ──→ "SUBMIT_TO_BLOCKCHAIN" OutboxMessage committed
     (REQUIRES_NEW)            (in derselben REQUIRES_NEW-TX)

Wenn System nach FUNDS_HELD crasht:
  OutboxProcessor (alle 5s) liest PENDING SUBMIT_TO_BLOCKCHAIN Nachrichten
  └── TX-Status prüfen:
        SETTLED/FAILED → OutboxMsg = SENT (bereits erledigt)
        SUBMITTED + circleId → Circle pollen → SETTLED oder FAILED
        FUNDS_HELD ohne circleId → ERROR-Alert (manueller Eingriff)
```

**Invariante:** Solange die SUBMIT_TO_BLOCKCHAIN OutboxMessage in `PENDING` ist,
wird der OutboxProcessor bis zu MAX_ATTEMPTS=5 versuchen die TX zu finalisieren.

### 3. Circuit Breaker + Retry für externe Systeme

| System | Retry | Circuit Breaker | Fallback |
|---|---|---|---|
| Chainalysis | — | `@CircuitBreaker(chainalysis)` | Fail-Closed (403) |
| Taurus Custody | 2x, 1s Backoff | `@CircuitBreaker(taurus-custody)` | `transitionToFailed()` + Hold-Release |
| Circle Wallet | 3x, 500ms Backoff | `@CircuitBreaker(circle-wallet)` | `transitionToFailed()` + Hold-Release |
| Core Banking | 3x, 500ms Backoff | `@CircuitBreaker(core-banking)` | `transitionToFailed()` + Hold-Release |

**Pattern:** Alle externen Aufrufe laufen über `self.*`-Wrapper-Methoden (Spring AOP-Proxy
für Resilience4j). Bei erschöpften Retries greift der Fallback, der atomar (REQUIRES_NEW)
den Status auf FAILED setzt und den Hold via `CoreBankingClient.releaseHold()` freigibt.

### 4. Auto-Hold-Release bei FAILED

```
FUNDS_HELD/SUBMITTED → transitionToFailed() [REQUIRES_NEW]
  ├── validateTransition(current, FAILED)
  ├── [current ∈ {FUNDS_HELD, SUBMITTED} AND holdId != null]
  │     → coreBankingClient.releaseHold(holdId)   ← automatisch!
  ├── tx.status = FAILED, failureReason gesetzt
  └── OutboxMessage TRANSACTION_FAILED
```

**Invariante:** Nach jedem FAILED-Übergang aus FUNDS_HELD oder SUBMITTED ist der
EUR-Hold immer freigegeben — kein manueller Cleanup notwendig.

### 5. Outbox-Recovery Sequenz (Crash nach FUNDS_HELD)

```
Normaler Betrieb:        [FUNDS_HELD] ─→ [SUBMITTED] ─→ [SETTLED]
                              ↓
                   SUBMIT_TO_BLOCKCHAIN (OutboxMsg PENDING)

Crash-Recovery:          [System startet neu]
                              ↓
                   OutboxProcessor liest SUBMIT_TO_BLOCKCHAIN
                              ↓
                   TX-Status = SUBMITTED, circleId gesetzt
                              ↓
                   circleWalletClient.getTransactionStatus(circleId)
                         ↙              ↘
                   COMPLETE            FAILED
                      ↓                  ↓
                 SETTLED            transitionToFailed()
             (blockchainHash)       (Hold freigegeben)
```

---

---

## UC-27: Inbound Stablecoin Empfang (Zahlungseingang via Blockchain-Webhook)

**Neu: 2026-08-18** | Commit `a607b2e`

Ein Dritter überweist USDC oder EURC an die Wallet eines Kunden. Circle/Taurus benachrichtigt die Plattform per Webhook. Nach Post-Receive AML-Screening wird der EUR-Gegenwert automatisch auf dem Girokonto gutgeschrieben.

### Akteure
- Circle / Taurus (Webhook-Sender)
- Chainalysis (AML-Screening, `direction="incoming"`)
- CoreBankingClient (Ledger-Buchung)
- InboundProcessingService (Orchestrierung)

### Ablauf

```
POST /api/v1/b2b/inbound/webhook (permitAll)
{walletId, amount, currency, blockchainHash, senderWallet}
  │
  ├─ 1. Cross-Tenant-Lookup: adminJdbcTemplate → Wallet → Account → tenantId
  ├─ 2. TenantContext.set(tenantId)
  ├─ 3. Idempotenz: findByBlockchainHash → 409 wenn Duplikat
  ├─ 4. TX: CREATED → INCOMING [REQUIRES_NEW, OutboxMsg: PROCESS_INBOUND_COMPLIANCE]
  │
  ├─── LOW/MEDIUM_RISK: ─────────────────────────────────────────────────────────
  │     COMPLIANCE_PENDING
  │     FX: EURC×1.0 | USDC×ECB-Kurs (kein FX-Spread, gebührenfrei)
  │     CoreBankingClient.createLedgerBooking(IBAN, amountEur, "INBOUND_CREDIT")
  │     COMPLIANCE_APPROVED → SETTLED (201 Created)
  │
  └─── HIGH_RISK (z.B. OFAC-Sanktionen): ───────────────────────────────────────
        COMPLIANCE_PENDING
        AuditLog: action='AML_INBOUND_BLOCK' (BaFin-Pflicht)
        COMPLIANCE_REJECTED → FAILED (201 Created, Gelder blockiert)
```

### Neue State Machine (Inbound-Erweiterung)

```
Outbound (bestehend):  CREATED → [PENDING_APPROVAL] → COMPLIANCE_CHECKED → FUNDS_HELD → SUBMITTED → SETTLED

Inbound (neu):         CREATED → INCOMING → COMPLIANCE_PENDING ──→ COMPLIANCE_APPROVED → SETTLED
                                                                 └─→ COMPLIANCE_REJECTED → FAILED
```

### FX-Berechnung

| Currency | Rate | Spread | Fee |
|---|---|---|---|
| EURC | 1.0 (parity) | 0% | 0,00 EUR |
| USDC | ECB SDMX-JSON (mock: 1.0823) | 0% | 0,00 EUR |

### Crash-Recovery
`OutboxProcessor.PROCESS_INBOUND_COMPLIANCE`: Bei Systemausfall nach `INCOMING` wird der Compliance-Flow beim Neustart automatisch neu gestartet (idempotent).

---

## UC-28: Mandantenisolation (Multi-Tenancy via PostgreSQL RLS)

**Neu: 2026-08-18** | Commit `68bb995`

Die Plattform ist mandantenfähig. Volksbank-Mandanten sehen ausschließlich ihre eigenen Daten. Die Isolation wird auf DB-Ebene durch PostgreSQL Row-Level Security erzwungen.

### Akteure
- Volksbank Kleinstadt eG (tenant-kleine-vb)
- Volksbank Metropole eG (tenant-grosse-vb)
- Marktbank AG (tenant-marktbank)
- Atruvia Dev/Default (tenant-default, Seed-Daten)

### Architektur

```
JWT {tenant: "tenant-kleine-vb"}
  └─ JwtAuthFilter → TenantContext.set("tenant-kleine-vb")
  └─ TenantAwareDataSource.getConnection()
       └─ set_config('app.current_tenant', 'tenant-kleine-vb', false)
  └─ PostgreSQL RLS-Policy:
       USING (tenant_id = current_setting('app.current_tenant', true))
  └─ Nur Rows mit tenant_id = 'tenant-kleine-vb' sichtbar
  └─ JwtAuthFilter.finally → TenantContext.clear()
```

### RLS-Tabellen

| Tabelle | tenant_id | Policy |
|---|---|---|
| `customer_account` | ✅ | `tenant_isolation_policy` |
| `stablecoin_transaction` | ✅ | `tenant_isolation_policy` |
| `address_book` | ✅ | `tenant_isolation_policy` |
| `yield_position` | ✅ | `tenant_isolation_policy` |
| `audit_log` | ✅ | `tenant_isolation_policy` |
| `institutional_address_book` | ❌ | Bank-weit (kein Tenant) |
| `approval_workflow` | ❌ | Internal (kein Tenant) |
| `outbox_message` | ❌ | Internal (kein Tenant) |

### Dev-Mandanten (V8 Seed)

| tenant_id | Name | Typ |
|---|---|---|
| `tenant-kleine-vb` | Volksbank Kleinstadt eG | COOPERATIVE |
| `tenant-grosse-vb` | Volksbank Metropole eG | COOPERATIVE |
| `tenant-marktbank` | Marktbank AG | BANK |
| `tenant-default` | Default Dev Tenant | DEV |

### Isolation-Beweis

```
Tenant A JWT → GET /accounts/DE89.../balance → 200 ✅ (eigener Account sichtbar)
Tenant B JWT → GET /accounts/DE89.../balance → 404 ✅ (RLS filtert fremden Account)
```

---

## Technologie-Übersicht

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21, Maven |
| Datenbank | PostgreSQL 16 + Flyway (V1–V9) |
| Multi-Tenancy | PostgreSQL RLS + TenantAwareDataSource + TenantContext (ThreadLocal) |
| Frontend | Angular 18, TypeScript, Standalone Components |
| Auth | JWT HS256, `tenant`-Claim für Mandantenidentifikation |
| FX-Rate | `FxRateService`: Mock (dev, 1.0823) / ECB SDMX-JSON (prod) |
| Externe APIs (Mock) | Circle (USDC/EURC), Taurus (MPC), Chainalysis (AML, direction param), n8n |
| Resilienz | Resilience4j Circuit Breaker + Transactional Outbox (Crash-Recovery) |
| Observability | OpenTelemetry Tracing, AuditLog (INSERT-only, inkl. `AML_INBOUND_BLOCK`) |
| Tests | JUnit 5, Spring Boot Test, Testcontainers PostgreSQL 16 · **106 Tests** |

## Flyway-Migrationen

| Version | Datei | Inhalt |
|---|---|---|
| V1 | `V1__init.sql` | Alle 8 Tabellen + Seed-Accounts (B2B + B2C) |
| V2 | `V2__fix_b2b_approval_threshold.sql` | `tx_limit_single` für `cust-b2b-001` → 25.000 EUR |
| V3 | `V3__add_institutional_address_book.sql` | Neue Tabelle `institutional_address_book` |
