# Use Cases — Atruvia Stablecoin Integration Platform (VERALTET — Archivdokument)

> ⚠️ **DEPRECATED — Dieses Dokument ist veraltet und dient nur noch als historisches Archiv.**
>
> **Bekannte Fehlinformationen:**
> - UC-17 (Yield-Auflösung): Zeigt `FAILED` als Redeem-Marker — **FALSCH**. Korrekt ist `REDEEMED` (seit State-Machine-Refactoring, `TransactionStatus.REDEEMED`)
> - UC-03 (Timeline): Zeigt Regex-basierten Code — **VERALTET**. Korrekt: `AuditLogRepository.findByTransactionIdOrderByTimestampAsc()` mit `fromStatus`/`toStatus`-Spalten
> - UC-Nummerierung: Entspricht nicht der aktuellen USE_CASES_v2.md-Nummerierung
>
> ➡️ **Für den aktuellen, vollständigen Stand aller 36+ Use Cases: [`USE_CASES_v2.md`](USE_CASES_v2.md) lesen.**
>
> Erstellt: 2026-08-17 | Changelog zuletzt aktualisiert: 2026-08-17  
> Commit-Abdeckung: `9252df7` (Erstellung) → `252fb57` (Tagesabschluss)

---

## Changelog

### 2026-08-17 — Commit `653ade6` · B2B Auslandsüberweisung Weiterentwicklung

| UC | Änderung |
|---|---|
| UC-01 | **Whitelist-Erzwingung:** Transfer schlägt mit 403 fehl wenn Zieladresse weder in Kunden- noch institutioneller Whitelist steht |
| UC-01 | **Live FX-Kurs:** `BASE_RATE` (hardcoded 1.0) durch `FxRateService` ersetzt — EURC=1.0, USDC=ECB-Referenzkurs (~1.0823) |
| UC-04 | **IAM Approver:** `approverId` kommt aus JWT (`auth.getName()`), nicht mehr aus Request-Body |
| UC-04 | **Selbst-Genehmigung:** Initiator ≠ Approver wird technisch erzwungen (`!devMode`) |
| UC-05 | **IAM Ablehner:** Analog UC-04 — `approverId` aus JWT, Selbst-Ablehnung blockiert |
| UC-06 | **Live FX-Kurs:** Rate-Quote für USDC liefert jetzt ~1.0839 statt 1.0015 |
| — | **NEU UC-23:** Nachtlicher Sanctions-Batch (`SanctionsBatchService`, cron 02:00) |

### 2026-08-17 — Commit `517fa52` · Ownership-Check, Institutionelle Whitelist, Balance-Widget, Tests

| UC | Änderung |
|---|---|
| UC-01 | **Institutionelle Whitelist (OR-Logik):** Transfer akzeptiert wenn Zieladresse in Kunden-ODER institutioneller Whitelist |
| UC-21 | **Ownership-Check:** `getBalance()` prüft ob angeforderte IBAN dem JWT-User gehört → 403 AUTH_001 bei Fremdzugriff |
| UC-21 | **Balance-Widget:** Frontend `TransferListComponent` zeigt jetzt EUR + USDC/EURC-Kontostand |
| UC-22 | **Ownership-Check:** `getTransaction()` prüft ob TX dem JWT-User gehört → 403 AUTH_001 |
| — | **NEU UC-24:** Institutionelle Adresse hinzufügen |
| — | **NEU UC-25:** Institutionelle Adressen auflisten |
| — | **NEU UC-26:** Institutionelle Adresse widerrufen |
| — | **NEU:** Integrationstests (Testcontainers, 5 Tests, `src/test/`) |

### 2026-08-17 — Commit `b8193eb` · State Machine Refactoring (BaFin/IT-Audit)

| UC | Änderung |
|---|---|
| UC-01 | **State Machine:** `PENDING` → `CREATED`, `AWAITING_APPROVAL` → `PENDING_APPROVAL`, `PROCESSING` → `FUNDS_HELD` + `SUBMITTED`, `BLOCKED` → `FAILED` |
| UC-01 | **Atomare Idempotenz:** Check + Insert in derselben `@Transactional` (Race Condition behoben) |
| UC-01 | **Circuit Breaker + Retry:** Circle und Taurus über `@Retry`/`@CircuitBreaker` Wrapper abgesichert |
| UC-01 | **Auto-Hold-Release:** FAILED aus FUNDS_HELD/SUBMITTED → `releaseHold()` automatisch |
| UC-04 | **Status APPROVED:** TX bekommt eigenen APPROVED-Status (vorher implizit im ApprovalWorkflow) |
| UC-05 | **Status REJECTED:** TX-Status `REJECTED` statt `FAILED` bei Ablehnung |
| — | **Neu:** Status `EXPIRED` für abgelaufene Approval-Fenster |
| — | **Neu:** `transitionTo()` mit ALLOWED_TRANSITIONS Map — ungültige Übergänge blocken mit IllegalStateException |

### 2026-08-17 — Commit `26d0dad` · YieldPosition Entity (BaFin/IT-Audit)

| UC | Änderung |
|---|---|
| UC-16 | **YieldPosition Entity:** `deposit()` erstellt jetzt YIELD_DEPOSIT TX (SETTLED, unveränderlich) + `YieldPosition` (ACTIVE) |
| UC-16 | **Response:** `depositId` → `positionId` (YieldPosition.id statt TX.id) |
| UC-17 | **YIELD_REDEEM TX:** `redeem()` erstellt eigenständige Auszahlungs-TX (Status: REDEEMED, Betrag: Anlage + Zinsen) |
| UC-17 | **YieldPosition CLOSED:** Position-Lebenszyklus getrennt von TX-Lebenszyklus |
| UC-18 | **getPosition():** Abfrage auf `YieldPositionRepository.findByStatus(ACTIVE)` — kein TX-Status-Hack mehr |
| — | **NEU:** `TransactionType.YIELD_REDEEM`, `YieldStatus` Enum (ACTIVE/CLOSED) |

### 2026-08-17 — Commit `7eff98d` · AuditLog relationales Schema (Performance + Indexierbarkeit)

| UC | Änderung |
|---|---|
| UC-03 | **buildTimeline():** Kein Regex-Parsing mehr — direkte Abfrage per `findByTransactionIdOrderByTimestampAsc()` |
| UC-22 | **buildTimeline():** Analog UC-03 |
| — | **AuditLog-Schema:** `previous_state`/`new_state` JSONB entfernt, neue Spalten: `transactionId` (FK+Index), `fromStatus`, `toStatus`, `details` (TEXT) |
| — | **TimelineEntry:** `(fromStatus, toStatus, performedBy, at, details)` statt `(status, at)` |
| — | **V7-Migration:** Datenmigration (JSONB → Spalten) + Partial Index auf `transaction_id` |

### 2026-08-17 — Commit `6431644` · Ausfallsicherheit (Resilience)

| UC | Änderung |
|---|---|
| UC-01 | **SUBMIT_TO_BLOCKCHAIN Outbox:** Bei `FUNDS_HELD` wird Outbox-Nachricht committed → Crash-Recovery |
| UC-01 | **Retry-Config:** `circle-wallet` (3x/500ms), `taurus-custody` (2x/1s), `core-banking` (3x/500ms) |
| — | **OutboxProcessor:** `recoverSubmitToBlockchain()` — finalisiert SUBMITTED-TX via Circle-API-Poll nach Neustart |

### 2026-08-17 — Commits `a7fc187` + `19e41ba` · Testabdeckung 13% → 62,7%

| Testklasse | TCs | Bereich |
|---|---|---|
| `OutboxProcessorTest` | 11 | Recovery-Logik |
| `ComplianceServiceTest` | 5 | AML-Screening |
| `GlobalExceptionHandlerTest` | 10 | Alle HTTP-Fehlercodes |
| `ExportServiceTest` | 8 | CAMT.053 + DATEV |
| `BulkPaymentServiceTest` | 10 | CSV-Validierung |
| `AddressBookServiceTest` | 7 | Whitelist-Management |
| `InstitutionalAddressBookServiceTest` | 6 | Bank-weite Whitelist |
| `B2bTransferIntegrationTest` | 4 | Transfer-Flows |
| `B2bResilienceTest` | 4 | Idempotenz + Recovery |
| `CommonControllerOwnershipTest` | 2 | Ownership-Check |
| — | **99 gesamt** | **LINE: 62,7% \| CLASS: 86,7%** |

---

## Übersicht

| # | Bereich | Use Case | Endpunkt | Status |
|---|---|---|---|---|
| UC-01 | B2B | Transfer initiieren | POST /api/v1/b2b/transfers | **Geändert** |
| UC-02 | B2B | Transfers auflisten | GET /api/v1/b2b/transfers | |
| UC-03 | B2B | Transfer-Detail abrufen | GET /api/v1/b2b/transfers/{id} | |
| UC-04 | B2B | Transfer freigeben (Vier-Augen) | POST /api/v1/b2b/transfers/{id}/approve | **Geändert** |
| UC-05 | B2B | Transfer ablehnen | POST /api/v1/b2b/transfers/{id}/reject | **Geändert** |
| UC-06 | B2B | FX-Kurs sichern (Rate-Quote) | GET /api/v1/b2b/rate-quote | **Geändert** |
| UC-07 | B2B | Adresse whitelisten | POST /api/v1/b2b/address-book | |
| UC-08 | B2B | Adressbuch auflisten | GET /api/v1/b2b/address-book | |
| UC-09 | B2B | Adresse widerrufen | DELETE /api/v1/b2b/address-book/{id} | |
| UC-10 | B2B | Bulk-Payment per CSV | POST /api/v1/b2b/bulk-payments | |
| UC-11 | B2B | Export CAMT.053 (ISO 20022) | GET /api/v1/b2b/export/camt053 | |
| UC-12 | B2B | Export DATEV-CSV | GET /api/v1/b2b/export/datev | |
| UC-13 | B2C | Auslandsüberweisung (Remittance) | POST /api/v1/b2c/remittances | |
| UC-14 | B2C | P2P-Zahlung via Telefonnummer | POST /api/v1/b2c/p2p/phone | |
| UC-15 | B2C | Telefon-Alias registrieren | POST /api/v1/b2c/p2p/phone/register | |
| UC-16 | B2C | Yield-Sparkonto eröffnen | POST /api/v1/b2c/savings/yield/deposit | |
| UC-17 | B2C | Yield-Position auflösen | DELETE /api/v1/b2c/savings/yield/{id} | |
| UC-18 | B2C | Yield-Position abrufen | GET /api/v1/b2c/savings/yield | |
| UC-19 | B2C | Card-Wallet abrufen | GET /api/v1/b2c/card/wallet | |
| UC-20 | B2C | Biometrie-Micropayment | POST /api/v1/b2c/micropayments | |
| UC-21 | Common | Kontostand abfragen | GET /api/v1/accounts/{iban}/balance | **Geändert** |
| UC-22 | Common | Transaktion abrufen | GET /api/v1/transactions/{id} | **Geändert** |
| UC-23 | B2B | Nachtlicher Sanctions-Batch | POST /api/v1/b2b/admin/sanctions-scan | **NEU** |
| UC-24 | B2B | Inst. Adresse hinzufügen | POST /api/v1/b2b/institutional-address-book | **NEU** |
| UC-25 | B2B | Inst. Adressen auflisten | GET /api/v1/b2b/institutional-address-book | **NEU** |
| UC-26 | B2B | Inst. Adresse widerrufen | DELETE /api/v1/b2b/institutional-address-book/{id} | **NEU** |

---

## B2B — Unternehmenskunden

---

### UC-01 · Transfer initiieren `[Geändert 2026-08-17]`

> **Änderungen:** Whitelist-Erzwingung + institutionelle Whitelist (OR-Logik) + Live FX-Kurs via ECB

**Summary**
Ein Firmenkunde löst eine Outbound-Stablecoin-Zahlung aus (EUR → USDC/EURC auf Polygon).
Der Service prüft Idempotenz, Whitelist-Zugehörigkeit (Kunden- oder institutionelle Liste),
führt Compliance-Screen und Blockchain-Settlement durch — oder parkt die TX bei Vier-Augen-Pflicht.

**Fachliche Einordnung**
- Kernprozess des "Turbo Rail": klassische SWIFT-Überweisung wird durch Circle + Taurus auf Blockchain ersetzt
- MiCA-Pflicht: AML-Screening (Chainalysis) vor jedem Settlement
- **[NEU]** Whitelist-Pflicht: Zieladresse muss ACTIVE in Kunden-Adressbuch ODER institutioneller Whitelist stehen
- Vier-Augen-Regel greift wenn `amountEur > txLimitSingle` (Seed: 25.000 EUR für B2B)
- Gebühr: 2,50 EUR Flat + 0,15% FX-Spread
- **[NEU]** FX-Rate: EURC=1.0 (fix), USDC=ECB-Referenzkurs live via `FxRateService`
- Rate-Quote optional: Kurs kann 60 Sekunden vorher gesichert werden

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController` | REST-Endpunkt, liest `X-Idempotency-Key` |
| `B2bTransferService` | Orchestrierung, kein eigenes `@Transactional` |
| `ComplianceService` | Chainalysis-Screen mit Circuit Breaker |
| `RevenueService` | Spread + Fee-Berechnung |
| `CoreBankingClient` | Hold + Ledger-Buchung |
| `TaurusCustodyClient` | MPC-Signatur + Blockchain-Submit |
| `CircleWalletClient` | USDC/EURC Transfer initiieren + Status pollen |
| `N8nWebhookClient` | Settlement-Notification (Fire & Forget) |
| `StablecoinTransactionRepository` | TX persistieren |
| `ApprovalWorkflowRepository` | Vier-Augen-Workflow |
| `RateQuoteRepository` | Quote konsumieren |
| `OutboxMessageRepository` | Transactional Outbox |
| `AuditLogRepository` | INSERT-only Audit Trail |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2b/transfers
  │  Header: X-Idempotency-Key
  ▼
B2bController.initiateTransfer()
  │  initiate(key, request, userId)
  ▼
B2bTransferService
  ├── DB: findByIdempotencyKey() → leer? weiter : 409 Conflict
  ├── self.persistInitialTransaction() [eigene @Transactional]
  │     ├── DB: RateQuote laden + USED setzen (optional)
  │     ├── DB: StablecoinTransaction INSERT (PENDING)
  │     ├── DB: OutboxMessage INSERT (TRANSACTION_INITIATED)
  │     └── DB: AuditLog INSERT (CREATED)
  │
  ├─ [amountEur > 25.000 EUR]
  │     ├── DB: ApprovalWorkflow INSERT (24h Fenster)
  │     ├── DB: TX status → AWAITING_APPROVAL
  │     └── ← 201 AWAITING_APPROVAL
  │
  └─ [amountEur ≤ 25.000 EUR] → executeTransferFlow()
        ├── self.updateStatus(COMPLIANCE_CHECK) [REQUIRES_NEW]
        ├── ComplianceService.screenAndAssert(wallet)
        │     ├── ChainalysisClient.screenAddress()
        │     ├── DB: AuditLog COMPLIANCE_SCREEN
        │     └── [HIGH_RISK] → ComplianceBlockException
        │           └── self.markBlocked() [REQUIRES_NEW]
        ├── self.updateStatus(PROCESSING) [REQUIRES_NEW]
        ├── CoreBankingClient.createHold(iban, EUR)
        ├── TaurusClient.signAndSubmit(currency, from, to, amount)
        ├── CircleClient.initiateTransfer(idempotencyKey, ...)
        │     └── CircleClient.getTransactionStatus() → COMPLETE + blockchainHash
        ├── RevenueService.calculate(amount, B2B)
        │     └── spread(0,15%) + fee(2,50 EUR) - gasCost = grossRevenue
        ├── CoreBankingClient.createLedgerBooking(transit + ertrag)
        ├── self.settleTransaction(hash, revenue) [REQUIRES_NEW]
        │     └── DB: TX SETTLED, blockchainHash, settledAt, revenue
        ├── N8nClient.notifySettlement() [best effort, Exception wird geloggt]
        ├── DB: OutboxMessage INSERT (TRANSACTION_SETTLED)
        └── ← 201 SETTLED TransactionResponse
```

**Code-Schnipsel**

```java
// Orchestrierung ohne eigenes @Transactional — alle Writes via self (AOP-Proxy)
public TransactionResponse initiate(String idempotencyKey,
        InitiateTransferRequest request, String initiatorId) {
    txRepository.findByIdempotencyKey(idempotencyKey)
        .ifPresent(ex -> { throw new IdempotencyConflictException(ex.getId()); });

    InitResult init = self.persistInitialTransaction(idempotencyKey, request, initiatorId);
    if (init.requiresApproval()) return init.response();

    StablecoinTransaction tx = txRepository.findById(init.txId()).orElseThrow();
    return executeTransferFlow(tx, initiatorId);
}

// Vier-Augen-Grenze aus CustomerAccount
boolean requiresApproval = request.amountEur()
    .compareTo(account.getTxLimitSingle()) > 0;

// Status-Update in eigener TX (REQUIRES_NEW → sofort committed, auch bei Rollback der äußeren TX)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void updateStatus(UUID txId, TransactionStatus newStatus, String userId) {
    StablecoinTransaction tx = txRepository.findById(txId).orElseThrow();
    tx.setStatus(newStatus);
    txRepository.save(tx);
    saveAuditLog("StablecoinTransaction", txId, "STATUS_CHANGED", ...);
}
```

**Wichtige Punkte**
- **Self-Injection** (`@Lazy @Autowired private B2bTransferService self`): Spring-AOP-Proxy wird für
  `REQUIRES_NEW`-Methoden benötigt — direktes `this.` würde den Proxy umgehen und die innere TX wäre wirkungslos
- **Transactional Outbox**: Jedes Statusereignis schreibt in `outbox_message` — Grundlage für
  zuverlässige Downstream-Events ohne direkten Message-Broker-Call
- **Rate-Quote-Verbrauch**: Quote wird atomar auf `USED` gesetzt — kein Doppelverbrauch möglich
- **Fehlerbehandlung**: `ComplianceBlockException` → `BLOCKED`, alles andere → `FAILED`,
  beide in eigener `REQUIRES_NEW`-TX damit der Fehler-Status trotz Rollback der Haupt-TX persistiert

---

### UC-02 · Transfers auflisten

**Summary**
Paginierte Liste aller Transfers des eingeloggten Firmenkunden, optional gefiltert nach Status.

**Fachliche Einordnung**
- Read-only-Query, kein externer API-Call
- Der JWT-Sub (`customerId`) wird als Mandanten-Filter verwendet
- Fallback auf alle TX wenn kein Account gefunden (implizite Admin-Sicht)

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController` | Query-Parameter (`status`, `page`, `size`) |
| `B2bTransferService.listTransfers()` | Paginierte DB-Abfrage |
| `CustomerAccountRepository` | Account per customerId |
| `StablecoinTransactionRepository` | TX-Abfrage mit/ohne Status-Filter |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/transfers?status=SETTLED&page=0&size=20
  ▼
B2bController.listTransfers()
  │  listTransfers(userId, status, page, size)
  ▼
B2bTransferService
  ├── CustomerAccountRepository.findByCustomerId(userId)
  │     ├── [gefunden]      → findByCustomerAccountId[AndStatus](pageable)
  │     └── [nicht gefunden] → findAll[ByStatus](pageable)  ← Admin-Fallback
  ├── Page<StablecoinTransaction> (Sort: createdAt DESC)
  ├── Pro TX: ApprovalRepository.findByTransactionId()  ← N+1-Risiko bei großen Pages
  └── ← 200 TransferPageResponse {content[], total, pages, page, size}
```

**Code-Schnipsel**

```java
public TransferPageResponse listTransfers(String userId,
        TransactionStatus statusFilter, int page, int size) {
    PageRequest pageable = PageRequest.of(page, size,
        Sort.by(Sort.Direction.DESC, "createdAt"));

    Page<StablecoinTransaction> txPage = accountRepository.findByCustomerId(userId)
        .map(account -> statusFilter != null
            ? txRepository.findByCustomerAccountIdAndStatus(account.getId(), statusFilter, pageable)
            : txRepository.findByCustomerAccountId(account.getId(), pageable))
        .orElseGet(() -> statusFilter != null
            ? txRepository.findByStatus(statusFilter, pageable)
            : txRepository.findAll(pageable));

    List<TransactionResponse> content = txPage.getContent().stream()
        .map(tx -> toResponse(tx,
            approvalRepository.findByTransactionId(tx.getId()).isPresent()))
        .toList();

    return new TransferPageResponse(content, txPage.getTotalElements(),
        txPage.getTotalPages(), page, size);
}
```

**Wichtige Punkte**
- N+1-Risiko: Pro TX wird `approvalRepository.findByTransactionId()` aufgerufen —
  bei großen Pages wäre ein Batch-Join effizienter
- Default-Sort: `createdAt DESC` — neueste zuerst
- `Optional.orElseGet()`: Wenn kein Account existiert, kommen alle TX zurück
  (in Prod sollte das per Role gesperrt sein)

---

### UC-03 · Transfer-Detail abrufen

**Summary**
Einzelne Transaktion per UUID mit vollständiger Status-Timeline aus dem AuditLog.

**Fachliche Einordnung**
- Timeline rekonstruiert den kompletten Statusverlauf (PENDING → COMPLIANCE_CHECK → PROCESSING → SETTLED)
- Basis für Frontend-Fortschrittsanzeige und Support-Debugging

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController` | `@PathVariable UUID id` |
| `B2bTransferService.getById()` | TX laden + Timeline aufbauen |
| `AuditLogRepository` | Alle Audit-Einträge zur TX, chronologisch |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/transfers/{id}
  ▼
B2bController.getTransfer()
  │  getById(UUID)
  ▼
B2bTransferService
  ├── TxRepository.findById(id) → 404 wenn nicht gefunden
  ├── ApprovalRepository.findByTransactionId(id) → requiresApproval flag
  ├── buildTimeline(txId)
  │     ├── AuditLog.findByEntityTypeAndEntityIdOrderByTimestamp()
  │     ├── Pro Eintrag: Regex auf newState JSON → "status":"XXX"
  │     ├── TransactionStatus.valueOf(match)
  │     └── LinkedHashMap<Status, Timestamp> → Duplikate entfernt, Reihenfolge erhalten
  └── ← 200 TransactionResponse + timeline[]
```

**Code-Schnipsel**

```java
// Timeline wird aus AuditLog-JSON-Snapshots per Regex rekonstruiert
private static final Pattern STATUS_PATTERN =
    Pattern.compile("\"status\"\\s*:\\s*\"([^\"]+)\"");

private List<TransactionResponse.TimelineEntry> buildTimeline(UUID txId) {
    List<AuditLog> entries = auditLogRepository
        .findByEntityTypeAndEntityIdOrderByTimestampAsc("StablecoinTransaction", txId);

    Map<TransactionStatus, LocalDateTime> seen = new LinkedHashMap<>();
    for (AuditLog entry : entries) {
        Matcher m = STATUS_PATTERN.matcher(entry.getNewState());
        if (!m.find()) continue;
        try {
            TransactionStatus status = TransactionStatus.valueOf(m.group(1));
            seen.putIfAbsent(status, entry.getTimestamp()); // erster Eintrag gewinnt
        } catch (IllegalArgumentException ignored) {}
    }
    return seen.entrySet().stream()
        .map(e -> new TransactionResponse.TimelineEntry(e.getKey(), e.getValue()))
        .toList();
}
```

**Wichtige Punkte**
- Timeline ist kein eigenes Feld — sie wird aus dem AuditLog rekonstruiert,
  der als Append-only Event-Store fungiert
- Regex auf JSON-String statt strukturiertem Parsing: pragmatisch, aber fragil
  wenn sich das JSON-Format ändert
- `LinkedHashMap` erhält die Reihenfolge der ersten Statusvorkommnisse

---

### UC-04 · Transfer freigeben (Vier-Augen) `[Geändert 2026-08-17]`

> **Änderungen:** `approverId` kommt jetzt aus JWT; Selbst-Genehmigung technisch unmöglich

**Summary**
Ein zweiter Autorisierter gibt einen `AWAITING_APPROVAL`-Transfer frei. Der ApprovalWorkflow
wird committed, dann läuft der normale Transfer-Flow durch.

**Fachliche Einordnung**
- MiCA-Anforderung: Transaktionen über 25.000 EUR brauchen Dual-Control
- 24h Approval-Fenster: läuft ab → `EXPIRED`, Transfer kann nicht mehr freigegeben werden
- **[NEU]** `approverId` wird aus dem JWT (`auth.getName()`) übernommen — Request-Body-Wert ignoriert
- **[NEU]** Selbst-Genehmigung: `workflow.getInitiatorId().equals(approverId)` → 400 BIZ_001 (außer `dev-mode=true`)

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.approveTransfer()` | POST mit `ApproveTransferRequest` |
| `B2bTransferService.approve()` | Approval committen + Flow starten |
| `B2bTransferService.commitApproval()` | `@Transactional` — Workflow auf APPROVED setzen |
| `ApprovalWorkflowRepository` | Workflow laden + speichern |

**Sequenzdiagramm**

```
Approver
  │  POST /api/v1/b2b/transfers/{id}/approve
  │  Body: {approverId}
  ▼
B2bController.approveTransfer()
  │  approve(id, request)
  ▼
B2bTransferService
  ├── self.commitApproval() [eigene @Transactional]
  │     ├── ApprovalWorkflow laden
  │     ├── [status ≠ PENDING_APPROVAL] → 400 IllegalState
  │     ├── [expiresAt < now]           → EXPIRED → 400
  │     ├── workflow: approverId, status=APPROVED, approvedAt=now
  │     ├── DB: ApprovalWorkflow speichern
  │     └── DB: AuditLog APPROVED
  ├── TX laden
  └── executeTransferFlow(tx, approverId)
        └── → identisch zu UC-01 (Compliance → Hold → Taurus → Circle → SETTLED)
```

**Code-Schnipsel**

```java
@Transactional
public UUID commitApproval(UUID transactionId, ApproveTransferRequest request) {
    ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId)
        .orElseThrow(() -> new NoSuchElementException("Approval workflow not found"));

    if (workflow.getStatus() != ApprovalStatus.PENDING_APPROVAL)
        throw new IllegalStateException("Not pending: " + workflow.getStatus());

    if (workflow.getExpiresAt().isBefore(LocalDateTime.now())) {
        workflow.setStatus(ApprovalStatus.EXPIRED);
        approvalRepository.save(workflow);
        throw new IllegalStateException("Approval window expired");
    }

    workflow.setApproverId(request.approverId());
    workflow.setStatus(ApprovalStatus.APPROVED);
    workflow.setApprovedAt(LocalDateTime.now());
    approvalRepository.save(workflow);
    return workflow.getTransaction().getId();
}
```

**Wichtige Punkte**
- `commitApproval` schreibt in eigener `@Transactional` damit der Approve-Status sicher
  committed ist, bevor `executeTransferFlow` startet
- Dasselbe `executeTransferFlow` wie in UC-01 — keine Code-Duplizierung
- 24h-Fenster gesetzt in `persistInitialTransaction`: `LocalDateTime.now().plusHours(24)`

---

### UC-05 · Transfer ablehnen `[Geändert 2026-08-17]`

> **Änderungen:** `approverId` aus JWT; Selbst-Ablehnung blockiert (analog UC-04)

**Summary**
Zweiter Autorisierter lehnt einen wartenden Transfer ab. Workflow → `REJECTED`, TX → `FAILED`.

**Fachliche Einordnung**
- Kein Transfer-Flow, kein externer API-Call
- `failureReason` enthält die ID des ablehnenden Approvers

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.rejectTransfer()` | POST /transfers/{id}/reject |
| `B2bTransferService.reject()` | `@Transactional` — alles in einer TX |

**Sequenzdiagramm**

```
Approver
  │  POST /api/v1/b2b/transfers/{id}/reject
  │  Body: {approverId}
  ▼
B2bController.rejectTransfer()
  │  reject(id, request) [@Transactional]
  ▼
B2bTransferService
  ├── ApprovalWorkflow laden
  ├── [status ≠ PENDING_APPROVAL] → 400
  ├── DB: workflow.status = REJECTED
  ├── DB: tx.status = FAILED
  │         tx.failureReason = "Rejected by: {approverId}"
  ├── DB: AuditLog REJECTED
  └── ← 200 TransactionResponse (FAILED)
```

**Code-Schnipsel**

```java
@Transactional
public TransactionResponse reject(UUID transactionId, ApproveTransferRequest request) {
    ApprovalWorkflow workflow = approvalRepository.findByTransactionId(transactionId)
        .orElseThrow(...);

    if (workflow.getStatus() != ApprovalStatus.PENDING_APPROVAL)
        throw new IllegalStateException("Not pending: " + workflow.getStatus());

    workflow.setStatus(ApprovalStatus.REJECTED);
    approvalRepository.save(workflow);

    StablecoinTransaction tx = workflow.getTransaction();
    tx.setStatus(TransactionStatus.FAILED);
    tx.setFailureReason("Rejected by: " + request.approverId());
    txRepository.save(tx);

    saveAuditLog("StablecoinTransaction", tx.getId(), "REJECTED",
        "{\"status\":\"AWAITING_APPROVAL\"}",
        String.format("{\"status\":\"FAILED\",\"rejectedBy\":\"%s\"}", request.approverId()),
        request.approverId());
    return toResponse(tx, false);
}
```

**Wichtige Punkte**
- Einzige Approve/Reject-Methode mit direkt `@Transactional` (keine REQUIRES_NEW nötig,
  da kein nachgelagerter Flow)
- Kein Hold auf dem Konto — wurde noch nicht gesetzt (Hold passiert erst in `executeTransferFlow`)
- AuditLog spiegelt `AWAITING_APPROVAL → FAILED` für die Timeline

---

### UC-06 · FX-Kurs sichern (Rate-Quote) `[Geändert 2026-08-17]`

> **Änderungen:** USDC-Rate kommt jetzt live vom ECB (war hardcoded 1.0)

**Summary**
Firmenkunde fragt einen verbindlichen EUR→USDC/EURC-Kurs ab, der 60 Sekunden gültig ist
und beim Transfer-Initiate verwendet werden kann.

**Fachliche Einordnung**
- MiCA Art. 23: Kurs muss dem Kunden vor Auftragserteilung mitgeteilt werden
- Quote-ID kann in `InitiateTransferRequest.rateQuoteId` übergeben werden
- Nach Verbrauch: Quote-Status → `USED`; nach Ablauf: `EXPIRED`
- **[NEU]** EURC: Basisrate=1.0 (1:1 mit EUR) | USDC: Basisrate=ECB EUR/USD-Referenzkurs via `FxRateService`
- Dev-Mock: `MockFxRateClient` → 1.0823 | Prod: `HttpEcbRateClient` → live ECB SDMX-JSON

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.getRateQuote()` | GET mit `amountEur`, `currency` |
| `B2bTransferService.createRateQuote()` | Rate berechnen, Quote persistieren |
| `RateQuoteRepository` | `rate_quote`-Tabelle |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/rate-quote?amountEur=10000&currency=USDC
  ▼
B2bController.getRateQuote()
  │  createRateQuote(10000, USDC, userId) [@Transactional]
  ▼
B2bTransferService
  ├── B2B-Account laden (erster gefundener)
  ├── rate = 1.0 + (1.0 × 0.0015) = 1.00150000
  ├── expiresAt = now + 60s
  ├── DB: RateQuote INSERT (status=ACTIVE)
  └── ← 200 {quoteId, amountEur, amountStablecoin, rate,
               spreadPercent, flatFee=2.50, expiresAt, validSeconds=60}
```

**Code-Schnipsel**

```java
@Transactional
public RateQuoteResponse createRateQuote(BigDecimal amountEur,
        StablecoinCurrency currency, String userId) {
    BigDecimal rate = BASE_RATE                      // 1.000000
        .add(BASE_RATE.multiply(fxSpread))           // + 0.001500
        .setScale(8, RoundingMode.HALF_UP);          // = 1.00150000

    LocalDateTime expiresAt = LocalDateTime.now()
        .plusSeconds(rateQuoteValiditySeconds);      // +60s

    RateQuote quote = new RateQuote();
    quote.setQuotedRate(rate);
    quote.setSpreadApplied(fxSpread);
    quote.setExpiresAt(expiresAt);
    rateQuoteRepository.save(quote);

    return new RateQuoteResponse(
        quote.getId(), amountEur,
        amountEur.multiply(rate).setScale(6, RoundingMode.HALF_UP).toPlainString(),
        rate,
        fxSpread.multiply(BigDecimal.valueOf(100)),  // Spread in %
        new BigDecimal("2.50"),                       // Flat-Fee
        expiresAt, rateQuoteValiditySeconds
    );
}
```

**Wichtige Punkte**
- Kurs ist statisch (Mock): `1.0 + FX-Spread` — in Prod käme der Live-Kurs
  von Circle/einem FX-Provider (offener Punkt in HANDOVER.md)
- Verbrauch in `persistInitialTransaction`: Quote-Validierung + `status = USED`
  in derselben TX wie die initiale TX → atomar

---

### UC-07 · Adresse whitelisten

**Summary**
Firmenkunde fügt eine Destination-Wallet dem Adressbuch hinzu. Chainalysis prüft die
Adresse vorab — nur LOW/MEDIUM-Risk wird akzeptiert.

**Fachliche Einordnung**
- MiCA + FATF: Nur vorab gescreente Wallets dürfen als Transfer-Ziel genutzt werden
- Circuit Breaker schützt: Chainalysis nicht erreichbar → Ablehnung (Fail-Closed)
- `0xDEAD000...` → HIGH_RISK → HTTP 403 `ComplianceBlockException`

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.addAddress()` | POST /address-book |
| `AddressBookService.addAddress()` | Screen + Persist |
| `ChainalysisClient` | Wallet-Screening |
| `AddressBookRepository` | Eintrag speichern |
| `AuditLogRepository` | Screening-Ergebnis loggen |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2b/address-book
  │  Body: {walletAddress, label, currency}
  ▼
B2bController.addAddress()
  │  addAddress(request, userId) [@CircuitBreaker(chainalysis)]
  ▼
AddressBookService
  ├── AccountRepository.findByCustomerId(userId)
  ├── ChainalysisClient.screenAddress(wallet, currency, POLYGON, outgoing)
  │
  ├─ [approved=false / HIGH_RISK]
  │     ├── DB: AuditLog ADDRESS_SCREENING_BLOCKED
  │     └── ← 403 ComplianceBlockException
  │
  ├─ [Chainalysis nicht erreichbar] → addAddressFallback()
  │     └── ← 403 ComplianceBlockException("UNAVAILABLE")  ← Fail-Closed!
  │
  └─ [approved=true / LOW|MEDIUM]
        ├── DB: AddressBook INSERT (status=ACTIVE, riskScore gespeichert)
        ├── DB: AuditLog ADDRESS_ADDED
        └── ← 201 AddressBookResponse
```

**Code-Schnipsel**

```java
@CircuitBreaker(name = "chainalysis", fallbackMethod = "addAddressFallback")
@Transactional
public AddressBookResponse addAddress(AddAddressRequest request, String userId) {
    AddressScreenResponseDto screening = chainalysisClient.screenAddress(
        new AddressScreenRequestDto(request.walletAddress(),
            request.currency().name(), "POLYGON", "outgoing"));

    if (!screening.approved()) {
        writeAuditLog(..., "ADDRESS_SCREENING_BLOCKED", ...);
        throw new ComplianceBlockException(request.walletAddress(), screening.riskScore());
    }

    AddressBook entry = new AddressBook();
    entry.setRiskScore(RiskScore.valueOf(screening.riskScore()));
    // ... weitere Felder, dann speichern
}

// Fallback: ComplianceBlockException direkt weiterwerfen (kein Re-Wrap)
private AddressBookResponse addAddressFallback(AddAddressRequest request,
        String userId, Throwable ex) {
    if (ex instanceof ComplianceBlockException cbe) throw cbe;
    throw new ComplianceBlockException(request.walletAddress(), "UNAVAILABLE");
}
```

**Wichtige Punkte**
- **Fail-Closed-Pattern**: Chainalysis-Ausfall → Adresse wird blockiert, nicht durchgelassen
- Circuit Breaker muss `ComplianceBlockException` als `ignore-exception` konfiguriert haben,
  sonst öffnet er nach jeder HIGH_RISK-Ablehnung und blockiert alle Folgeaufrufe
- `RiskScore` wird auf der Adresse gespeichert → historische Risikoklasse abrufbar

---

### UC-08 · Adressbuch auflisten

**Summary**
Liste aller aktiven (nicht widerrufenen) Whitelist-Einträge des Firmenkunden.

**Fachliche Einordnung**
- Nur `ACTIVE`-Einträge werden zurückgegeben — widerrufene bleiben in der DB (Audit)
- Gefiltert nach Account des eingeloggten Users

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `AddressBookService.listAddresses()` | `readOnly=true` Query |
| `AddressBookRepository` | `findByCustomerAccountIdAndStatus(ACTIVE)` |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/address-book
  ▼
B2bController.listAddresses()
  │  listAddresses(userId) [readOnly=true]
  ▼
AddressBookService
  ├── AccountRepository.findByCustomerId(userId)
  ├── AddressBookRepository.findByCustomerAccountIdAndStatus(ACTIVE)
  └── ← 200 List<AddressBookResponse>  (REVOKED-Einträge gefiltert)
```

**Code-Schnipsel**

```java
@Transactional(readOnly = true)
public List<AddressBookResponse> listAddresses(String userId) {
    return accountRepository.findByCustomerId(userId)
        .map(account -> addressBookRepository
            .findByCustomerAccountIdAndStatus(account.getId(), AddressStatus.ACTIVE)
            .stream().map(this::toResponse).toList())
        .orElse(List.of());
}
```

**Wichtige Punkte**
- Soft-Delete: Widerrufene Adressen bleiben in der DB, werden aber nicht ausgeliefert —
  wichtig für den vollständigen Audit Trail

---

### UC-09 · Adresse widerrufen

**Summary**
Soft-Delete einer Whitelist-Adresse: Status → `REVOKED`, Eintrag bleibt in der DB.

**Fachliche Einordnung**
- Keine physische Löschung (AuditLog-Pflicht nach MiCA)
- Nach Widerruf kann die Adresse nicht mehr als Transfer-Ziel verwendet werden
  (Whitelist-Erzwingung im Transfer-Flow ist offener Punkt)

**Sequenzdiagramm**

```
Client
  │  DELETE /api/v1/b2b/address-book/{id}
  ▼
B2bController.revokeAddress()
  │  revokeAddress(id, userId) [@Transactional]
  ▼
AddressBookService
  ├── AddressBookRepository.findById(id) → 404 wenn nicht gefunden
  ├── DB: address.status = REVOKED  (Soft-Delete)
  ├── DB: AuditLog ADDRESS_REVOKED
  └── ← 204 No Content
```

**Code-Schnipsel**

```java
@Transactional
public void revokeAddress(UUID addressId, String userId) {
    AddressBook address = addressBookRepository.findById(addressId)
        .orElseThrow(() -> new NoSuchElementException("Address not found: " + addressId));

    address.setStatus(AddressStatus.REVOKED);
    addressBookRepository.save(address);

    writeAuditLog(addressId, "ADDRESS_REVOKED", userId,
        String.format("{\"address\":\"%s\",\"label\":\"%s\"}",
            address.getWalletAddress(), address.getLabel()));
}
```

**Wichtige Punkte**
- Controller gibt `204 No Content` zurück — korrekt für DELETE
- Offener Punkt (HANDOVER.md): Whitelist-Erzwingung im Transfer-Flow fehlt noch

---

### UC-10 · Bulk-Payment per CSV

**Summary**
CSV-Upload mit beliebig vielen Transfer-Zeilen. Jede Zeile wird als eigener Transfer
initiiert. Das Ergebnis enthält eine Per-Row-Auswertung mit Erfolg/Fehler.

**Fachliche Einordnung**
- Für große Zahlungsläufe von Firmenkunden (z.B. Lieferantenzahlungen)
- Jede Zeile erhält eine eigene zufällige `X-Idempotency-Key`
- CSV-Format: `destinationWallet,amountEur,currency,reference`
- Validierung: ETH-Wallet-Format, positiver Betrag, gültige Currency

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.bulkPayments()` | Multipart-Upload |
| `BulkPaymentService.process()` | CSV parsen, pro Zeile `transferService.initiate()` |
| `B2bTransferService.initiate()` | Jede Zeile = normaler Transfer (UC-01) |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2b/bulk-payments
  │  Header: X-Idempotency-Key
  │  Param: sourceIban
  │  Body: CSV-Datei (multipart/form-data)
  ▼
B2bController.bulkPayments()
  │  process(file, sourceIban, userId)
  ▼
BulkPaymentService
  ├── CSV einlesen (UTF-8, BufferedReader)
  ├── [Zeile 1 beginnt mit "destinationwallet"] → Header überspringen
  │
  └── Pro Datenzeile:
        ├── [< 4 Spalten]              → ERROR: invalid CSV format
        ├── [wallet ≠ ETH_ADDRESS]     → ERROR: invalid wallet address
        ├── [amount ≤ 0 / kein Dezimal] → ERROR: invalid amount
        ├── [currency unbekannt]        → ERROR: unknown currency
        └── [alles OK]
              ├── idempotencyKey = UUID.randomUUID()
              ├── B2bTransferService.initiate() → UC-01 vollständig
              │     (inkl. Vier-Augen wenn > 25.000 EUR)
              └── row = {status: OK, txId}

  └── ← 200 BulkPaymentResult {total, successful, failed, rows[]}
```

**Code-Schnipsel**

```java
private static final Pattern ETH_ADDRESS = Pattern.compile("^0x[0-9a-fA-F]{40}$");

// Pro Zeile: eigener Idempotency-Key
String idempotencyKey = UUID.randomUUID().toString();
InitiateTransferRequest request = new InitiateTransferRequest(
    sourceIban, destinationWallet, amountEur, currency,
    null, null, reference);
TransactionResponse response = transferService.initiate(
    idempotencyKey, request, initiatorId);
```

**Wichtige Punkte**
- Kein globaler Batch-Rollback: Wenn Zeile 5 fehlschlägt, sind Zeilen 1–4 bereits committed
- Header-Erkennung case-insensitiv: beginnt mit `destinationwallet` → übersprungen
- Vier-Augen greift auch pro Zeile — Zeilen > 25.000 EUR landen in `AWAITING_APPROVAL`

---

### UC-11 · Export CAMT.053 (ISO 20022)

**Summary**
Download aller SETTLED Transaktionen als ISO-20022-konformes CAMT.053-XML
für ERP/Buchhaltungssysteme.

**Fachliche Einordnung**
- CAMT.053 (Cash Management – Bank to Customer Statement) ist Standard im europäischen Zahlungsverkehr
- Nur SETTLED TX werden exportiert
- Enthält: Balance-Summe, je TX Betrag/Datum/BlockchainHash/TransactionCode PMNT/ICDT/ESCT

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.exportCamt053()` | GET, setzt Content-Disposition Header |
| `ExportService.generateCamt053()` | SETTLED TX laden, XML aufbauen |
| `ExportService.buildCamt053Xml()` | DOM-API, XML serialisieren |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/export/camt053?iban=DE89...
  ▼
B2bController.exportCamt053()
  ├── ExportService.resolveIban(ibanParam)
  │     └── [kein Param] → erste B2B-IBAN aus DB
  │   generateCamt053(iban)
  ▼
ExportService
  ├── AccountRepository.findByIban(iban)
  ├── TxRepository.findByAccountIdAndStatus(SETTLED, Pageable.unpaged())
  └── buildCamt053Xml()
        ├── DOM: Document / BkToCstmrStmt / GrpHdr (MsgId, CreDtTm)
        ├── Stmt / Acct / IBAN
        ├── Bal (Summe aller SETTLED EUR-Beträge, Typ=CLBD)
        └── Pro TX: Ntry
              ├── Amt (EUR), CdtDbtInd=DBIT
              ├── Sts/Cd=BOOK, BookgDt, NtryRef (TX-UUID)
              ├── BkTxCd: PMNT / ICDT / ESCT
              ├── NtryDtls/TxDtls/Refs/EndToEndId
              └── AddtlNtryInf = blockchainHash  ← On-Chain-Verifikation!

  ← 200 application/xml
    Content-Disposition: attachment; filename="camt053-{IBAN}.xml"
```

**Code-Schnipsel**

```java
// Blockchain-Hash im Standard-Feld AddtlNtryInf
String hash = tx.getBlockchainHash() != null ? tx.getBlockchainHash() : "N/A";
addText(doc, ntry, "AddtlNtryInf", hash);

// Response mit Download-Header
return ResponseEntity.ok()
    .contentType(MediaType.APPLICATION_XML)
    .header(HttpHeaders.CONTENT_DISPOSITION,
        "attachment; filename=\"camt053-" + resolvedIban + ".xml\"")
    .body(xml.getBytes(StandardCharsets.UTF_8));
```

**Wichtige Punkte**
- Blockchain-Hash in `AddtlNtryInf` — erlaubt On-Chain-Verifizierung direkt aus dem Kontoauszug
- `resolveIban()`: Ohne Parameter nimmt der Service automatisch die erste B2B-IBAN

---

### UC-12 · Export DATEV-CSV

**Summary**
Download aller SETTLED Transaktionen als DATEV-kompatibles CSV für
deutsches Steuerrecht und Buchhaltung.

**Fachliche Einordnung**
- DATEV-Format: Datum, Belegnummer (TX-UUID), EUR-Betrag, Stablecoin-Betrag,
  Währung, Hash, Bruttoertrag, Status
- Dateiname enthält Datum: `datev-export-{IBAN}-2026-08-17.csv`

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/export/datev?iban=DE89...
  ▼
B2bController.exportDatev()
  ├── ExportService.resolveIban(ibanParam)
  │   generateDatev(iban)
  ▼
ExportService
  ├── AccountRepository.findByIban(iban)
  ├── TxRepository.findByAccountIdAndStatus(SETTLED, unpaged)
  └── CSV aufbauen
        ├── Header: Datum,Belegnummer,Betrag_EUR,Betrag_Stablecoin,
        │           Waehrung,Blockchain_Hash,Bruttoertrag_EUR,Status
        ├── Pro TX: eine Zeile mit csvEscape()
        └── Dateiname: datev-export-{IBAN}-{yyyy-MM-dd}.csv

  ← 200 text/csv;charset=UTF-8
    Content-Disposition: attachment; filename="datev-export-{IBAN}-{datum}.csv"
```

**Code-Schnipsel**

```java
// CSV-Escaping: Werte mit Komma, Anführungszeichen oder Newline werden quoted
private String csvEscape(String value) {
    if (value == null) return "";
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
}
```

**Wichtige Punkte**
- `Bruttoertrag_EUR` erlaubt direkte Erlösverbuchung in DATEV ohne manuelle Aufspaltung
- UTF-8-Encoding explizit gesetzt → korrekte Darstellung von Umlauten in DATEV

---

## B2C — Privatkunden

---

### UC-13 · Auslandsüberweisung (Remittance)

**Summary**
Privatkunde überweist Geld ins Ausland. EUR werden in USDC umgewandelt und über Circle
zum Empfänger-Gateway transferiert. Dem Absender wird der Lokalbetrag angezeigt.

**Fachliche Einordnung**
- B2C-Kernprodukt: "Turbo Rail" für Privatpersonen
- Keine Blockchain-Begriffe in der UI — der Nutzer sieht nur "Auslandsüberweisung"
- Gebühr: 0,50 EUR flat
- Länder-Mapping: MX→MXN (×18.2), PH→PHP, IN→INR, NG→NGN, default→USD
- Chainalysis-Screen: Absender-Wallet wird geprüft (nicht das Ziel-Gateway)
- Ziel: immer `REMITTANCE_GATEWAY_WALLET` (internes Routing-Wallet)

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2cController` | POST /remittances |
| `B2cRemittanceService.send()` | Compliance, Circle, Revenue, Settlement |
| `ChainalysisClient` | Absender-Wallet Screen |
| `CircleWalletClient` | USDC-Transfer |
| `RevenueService` | Ertrag berechnen |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/remittances
  │  Header: X-Idempotency-Key
  │  Body: {sourceIban, amountEur, recipientName, recipientCountry}
  ▼
B2cController → B2cRemittanceService.send() [@Transactional]
  │
  ├── DB: findByIdempotencyKey() → 409 wenn Duplikat
  ├── AccountRepository.findByIban(sourceIban)
  ├── ChainalysisClient.screenAddress(account.wallet, USDC, MATIC, outgoing)
  │     └── [!approved] → 403 ComplianceBlockException
  ├── DB: TX INSERT (REMITTANCE, USDC, PROCESSING, → GATEWAY_WALLET)
  ├── CircleClient.initiateTransfer(→ REMITTANCE_GATEWAY_WALLET)
  ├── RevenueService.calculate(amount, B2C)
  │     └── spread(0,15%) + fee(0,50 EUR) - gasCost = grossRevenue
  ├── DB: TX UPDATE (SETTLED, circleId, settledAt, grossRevenue)
  ├── DB: AuditLog REMITTANCE_SENT
  └── ← 200 RemittanceResponse
        {txId, status=SETTLED, fee=0.50,
         amountLocal="182.00 MXN",        ← Länder-Mapping: MX × 18.2
         duration="< 30 Sekunden",
         trackingCode="ATR-{8 Hex-Zeichen}"}
```

**Code-Schnipsel**

```java
// Lokalbetrag-Umrechnung (statischer Kurs)
private static final BigDecimal MXN_RATE = new BigDecimal("18.2");

private static String currencyForCountry(String countryCode) {
    return switch (countryCode == null ? "" : countryCode.toUpperCase()) {
        case "MX" -> "MXN"; case "PH" -> "PHP";
        case "IN" -> "INR"; case "NG" -> "NGN";
        default   -> "USD";
    };
}

// Tracking-Code deterministisch aus TX-UUID
String trackingCode = "ATR-" + savedTx.getId().toString()
    .replace("-", "").substring(0, 8).toUpperCase();
```

**Wichtige Punkte**
- Alles in einer `@Transactional`: Compliance-Check, DB-Insert, Circle-Call, Settlement —
  kein REQUIRES_NEW-Splitting wie bei B2B
- Tracking-Code ist deterministisch aus der TX-ID ableitbar — kein extra Feld nötig
- Live-FX-Kurs für Lokalbetrag ist offener Punkt (derzeit hardgecodet)

---

### UC-14 · P2P-Zahlung via Telefonnummer

**Summary**
Privatkunde sendet Geld an eine Telefonnummer. Die Nummer wird gehasht, das zugehörige
Wallet aus der DB gesucht, dann erfolgt der Circle-Transfer direkt an die Empfänger-Wallet.

**Fachliche Einordnung**
- Kein Blockchain-Begriff für den Nutzer — "Nummer eingeben, Betrag senden"
- Gebühr: 0,00 EUR (P2P ist kostenlos)
- Phone-Hashing schützt die Nummer: SHA-256 + Salt `atruvia-stablecoin-2026`
- Kein AML-Screen bei P2P (in Prod sollte auch hier gecheckt werden)

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2cController` | POST /p2p/phone |
| `B2cP2pService.sendToPhone()` | Hash-Lookup, TX, Circle |
| `PhoneAliasRepository` | Lookup per Hash |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/p2p/phone
  │  Header: X-Idempotency-Key
  │  Body: {sourceIban, recipientPhone, amountEur}
  ▼
B2cController → B2cP2pService.sendToPhone() [@Transactional]
  │
  ├── DB: findByIdempotencyKey() → 409 wenn Duplikat
  ├── hashPhoneNumber(recipientPhone)
  │     └── SHA-256("atruvia-stablecoin-2026" + phone) → 64-Hex-String
  ├── PhoneAliasRepository.findByPhoneNumberHash(hash)
  │     └── [nicht gefunden] → 404 NoSuchElementException
  ├── AccountRepository.findByIban(sourceIban)  ← Sender
  ├── DB: TX INSERT (P2P, USDC, PROCESSING,
  │         sourceWallet=sender.wallet, destWallet=recipientAlias.wallet)
  ├── CircleClient.initiateTransfer(→ recipientAlias.walletAddress)
  ├── RevenueService.calculate(amount, B2C)
  ├── DB: TX UPDATE (SETTLED, circleId, settledAt, grossRevenue)
  ├── DB: AuditLog P2P_SENT
  └── ← 200 TransactionResponse (SETTLED)
```

**Code-Schnipsel**

```java
private static final String PHONE_SALT = "atruvia-stablecoin-2026";

private String hashPhoneNumber(String phoneNumber) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    String salted = PHONE_SALT + phoneNumber;
    byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
    StringBuilder sb = new StringBuilder(64);
    for (byte b : hash) sb.append(String.format("%02x", b));
    return sb.toString();
}
```

**Wichtige Punkte**
- **Privacy-by-Design**: Telefonnummern werden nie im Klartext gespeichert — nur der Hash
- Salt muss beim direkten DB-Insert exakt stimmen: `SHA-256("atruvia-stablecoin-2026" + nummer)`
- Empfänger muss sich vorab registriert haben (UC-15), sonst 404

---

### UC-15 · Telefon-Alias registrieren

**Summary**
Privatkunde verknüpft seine Telefonnummer mit seiner Wallet-Adresse.
Die Nummer wird gehasht gespeichert.

**Fachliche Einordnung**
- Einmalige Registrierung — danach empfangbar für P2P-Zahlungen
- Kein Duplikat-Check: zweite Registrierung derselben Nummer legt neuen Eintrag an

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/p2p/phone/register
  │  Body: {sourceIban, phoneNumber, walletAddress}
  ▼
B2cController → B2cP2pService.registerPhoneAlias() [@Transactional]
  │
  ├── AccountRepository.findByIban(sourceIban)
  ├── hashPhoneNumber(phoneNumber)
  │     └── SHA-256("atruvia-stablecoin-2026" + phone) → Hash
  ├── DB: PhoneAlias INSERT {phoneNumberHash, walletAddress, account}
  │         (Nummer nie im Klartext!)
  ├── DB: AuditLog PHONE_ALIAS_REGISTERED
  └── ← 201 Created (kein Body)
```

**Code-Schnipsel**

```java
@Transactional
public void registerPhoneAlias(RegisterPhoneAliasRequest request, String userId) {
    CustomerAccount account = accountRepository.findByIban(request.sourceIban())
        .orElseThrow(...);

    String hash = hashPhoneNumber(request.phoneNumber()); // SHA-256 + Salt

    PhoneAlias alias = new PhoneAlias();
    alias.setPhoneNumberHash(hash);
    alias.setWalletAddress(request.walletAddress());
    alias.setCustomerAccount(account);
    phoneAliasRepository.save(alias);

    saveAuditLog(account.getId(), "PhoneAlias", "PHONE_ALIAS_REGISTERED", ...);
}
```

**Wichtige Punkte**
- `walletAddress` kommt vom Client — in Prod sollte das die verifizierte Wallet des Accounts sein
- Controller gibt `201 Created` ohne Body zurück

---

### UC-16 · Yield-Sparkonto eröffnen

**Summary**
Privatkunde legt einen Betrag in EURC an — abgebildet als Investition in einen
RWA-Money-Market-Fund. Rendite: 3,5% p.a. täglich compoundiert.

**Fachliche Einordnung**
- Kein "Krypto" in der UI — Kunde sieht "Sparkonto" mit Zinssatz
- EURC wird an `RWA_FUND_WALLET` transferiert
- Zinsformel: `principal × (1 + 0.035/365)^days`
- Sofort `SETTLED` — kein Circle-Call im Deposit (vereinfacht)

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2cController` | POST /savings/yield/deposit |
| `B2cYieldService.deposit()` | TX anlegen, Tagesertrag berechnen |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/savings/yield/deposit
  │  Header: X-Idempotency-Key
  │  Body: {sourceIban, amountEur}
  ▼
B2cController → B2cYieldService.deposit() [@Transactional]
  │
  ├── DB: findByIdempotencyKey() → 409 wenn Duplikat
  ├── AccountRepository.findByIban(sourceIban)
  ├── DB: TX INSERT (YIELD_DEPOSIT, EURC, SETTLED, → RWA_FUND_WALLET, settledAt=now)
  ├── DB: AuditLog YIELD_DEPOSIT_CREATED
  ├── dailyYield = amountEur × 0.035 / 365
  └── ← 200 YieldPositionResponse
        {txId, principal, currentValue=principal,
         accruedYield=dailyYield, annualRate=3.5%,
         status="ACTIVE", depositedAt}
```

**Code-Schnipsel**

```java
// Zinseszins-Berechnung (täglich compoundiert)
private BigDecimal computeCurrentValue(BigDecimal principal, long days) {
    BigDecimal dailyRate = ANNUAL_RATE_DECIMAL
        .divide(DAYS_PER_YEAR, MathContext.DECIMAL64);     // 0.035 / 365
    BigDecimal factor = BigDecimal.ONE
        .add(dailyRate, MathContext.DECIMAL64)
        .pow((int) Math.min(days, Integer.MAX_VALUE), MathContext.DECIMAL64);
    return principal.multiply(factor, MathContext.DECIMAL64)
        .setScale(6, RoundingMode.HALF_UP);
}
```

**Wichtige Punkte**
- `MathContext.DECIMAL64` für Zinspotenzierung — verhindert Präzisionsverlust
- Nur eine aktive Position pro Kunde möglich (da `getPosition()` die erste SETTLED
  YIELD_DEPOSIT TX sucht)
- Status direkt `SETTLED` — kein Circle-Call (offener Punkt für Prod)

---

### UC-17 · Yield-Position auflösen (Redeem)

**Summary**
Kunde löst seine Yield-Position auf. Aufgelaufene Zinsen werden berechnet und geloggt,
die TX erhält Status `FAILED` als internen Redeem-Marker.

**Fachliche Einordnung**
- `FAILED` als Redeem-Marker: absichtlich — `getPosition()` sucht nur `SETTLED`-Einträge,
  daher verschwindet die Position nach Redeem automatisch
- Kein Auszahlungs-Transfer implementiert (offener Punkt)

**Sequenzdiagramm**

```
Client
  │  DELETE /api/v1/b2c/savings/yield/{id}
  ▼
B2cController → B2cYieldService.redeem() [@Transactional]
  │
  ├── TxRepository.findById(id)
  ├── [type ≠ YIELD_DEPOSIT] → 404
  ├── [status ≠ SETTLED]     → 400 (Position nicht aktiv)
  ├── days = ChronoUnit.DAYS.between(settledAt, now)
  ├── currentValue = principal × (1 + 0.035/365)^days  [DECIMAL64]
  ├── accrued = currentValue − principal
  ├── DB: tx.status = FAILED  ← Redeem-Marker!
  ├── DB: AuditLog YIELD_REDEEMED {principal, accruedYield, daysSinceDeposit}
  └── ← 204 No Content
```

**Code-Schnipsel**

```java
long days = ChronoUnit.DAYS.between(tx.getSettledAt(), LocalDateTime.now());
BigDecimal currentValue = computeCurrentValue(tx.getAmountFiat(), days);
BigDecimal accrued = currentValue.subtract(tx.getAmountFiat())
    .setScale(6, RoundingMode.HALF_UP);

tx.setStatus(TransactionStatus.FAILED);  // Redeem-Marker — dokumentiertes Design!
txRepository.save(tx);
```

**Wichtige Punkte**
- `FAILED` als Redeem-Status ist eine bewusste Designentscheidung (in HANDOVER.md dokumentiert)
- Aufgelaufene Zinsen nur im AuditLog sichtbar — kein eigenes Auszahlungs-TX

---

### UC-18 · Yield-Position abrufen

**Summary**
Gibt die aktuelle Yield-Position des Kunden zurück, inkl. aktuellem Wert mit
aufgelaufenen Zinsen (live berechnet).

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2c/savings/yield
  ▼
B2cController → B2cYieldService.getPosition() [readOnly]
  │
  ├── AccountRepository.findByCustomerId(userId)
  ├── TxRepository.findFirstBy...Type=YIELD_DEPOSIT + Status=SETTLED
  │     └── [nicht gefunden] → 404 (keine aktive Position)
  └── calculatePosition(tx)
        ├── days = ChronoUnit.DAYS.between(depositedAt, now)
        ├── currentValue = principal × (1 + 0.035/365)^days
        ├── accrued = currentValue − principal
        ├── dailyYield = principal × 0.035 / 365
        └── ← 200 YieldPositionResponse (live berechnet, kein Cache)
```

**Wichtige Punkte**
- Zinsen werden bei jedem GET-Aufruf frisch berechnet — immer aktuell,
  aber bei sehr langer Laufzeit potenziell teuer (`BigDecimal.pow` mit großem Exponent)

---

### UC-19 · Card-Wallet abrufen

**Summary**
Gibt USDC- und EURC-Guthaben des persönlichen Wallets zurück —
dient der Anzeige im Karten-Widget der B2C-UI.

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2cMicropaymentService.getCardWallet()` | Wallet-Balance von Circle |
| `CircleWalletClient.getWalletBalance()` | Mock: feste Balances |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2c/card/wallet
  ▼
B2cController → B2cMicropaymentService.getCardWallet() [readOnly]
  │
  ├── AccountRepository.findByCustomerId(userId)
  ├── CircleClient.getWalletBalance(account.walletAddress)
  │     └── Mock: gibt feste USDC + EURC Balance zurück
  ├── USDC-Balance filtern
  ├── EURC-Balance filtern
  └── ← 200 CardWalletResponse {walletAddress, usdc, eurc}
```

**Code-Schnipsel**

```java
String usdc = balance.balances().stream()
    .filter(b -> "USDC".equalsIgnoreCase(b.currency()))
    .map(CircleWalletBalanceDto.Balance::amount)
    .findFirst().orElse("0.000000");
```

**Wichtige Punkte**
- Im Dev-Profil liefert der Mock immer eine feste Balance
- Wallet-Adresse des Accounts wird als ID für den Circle-API-Call verwendet

---

### UC-20 · Biometrie-Micropayment

**Summary**
Kleinstbetragszahlung (max. 10 EUR) per biometrischer Bestätigung an einen Händler.
Ziel-Wallet wird aus der Merchant-ID deterministisch abgeleitet.

**Fachliche Einordnung**
- Kein PIN, kein 2FA — Biometrie als einzige Autorisierung
- `biometricToken` wird formal validiert (min. 10 Zeichen) — kein echtes Biometrie-System im Mock
- Gebühr: 0,10 EUR
- Max 10 EUR — Mikrozahlungen für digitale Inhalte

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2cMicropaymentService.pay()` | Validierung, TX, Circle |
| `resolveMerchantWallet()` | Deterministische Wallet-Ableitung aus MerchantID |

**Sequenzdiagramm**

```
Client
  │  POST /api/v1/b2c/micropayments
  │  Header: X-Idempotency-Key
  │  Body: {biometricToken, amountEur, sourceIban, destinationMerchantId, contentId}
  ▼
B2cController → B2cMicropaymentService.pay() [@Transactional]
  │
  ├── [biometricToken.length < 10]   → 400
  ├── [amountEur > 10.00 EUR]        → 400 (Max überschritten)
  ├── DB: findByIdempotencyKey()     → 409 wenn Duplikat
  ├── AccountRepository.findByIban(sourceIban)
  ├── resolveMerchantWallet(merchantId)
  │     └── "0xMerchantL2Wallet" + sanitize(merchantId).padTo(22)  ← deterministisch
  ├── DB: TX INSERT (P2P, USDC, PROCESSING, fee=0.10 EUR)
  ├── CircleClient.initiateTransfer(→ merchantWallet)
  ├── DB: TX UPDATE (SETTLED, circleId, settledAt, grossRevenue=0.10)
  ├── DB: AuditLog MICROPAYMENT_SETTLED
  └── ← 200 TransactionResponse (SETTLED)
```

**Code-Schnipsel**

```java
// Max-Betrag-Prüfung
if (request.amountEur().compareTo(MAX_MICROPAYMENT_EUR) > 0) {
    throw new IllegalArgumentException(
        String.format("Micropayment amount %.2f EUR exceeds maximum of %.2f EUR",
            request.amountEur(), MAX_MICROPAYMENT_EUR));
}

// Merchant-Wallet deterministisch ableiten — gleiche merchantId → immer gleiche Wallet
private String resolveMerchantWallet(String merchantId) {
    String sanitized = merchantId.replaceAll("[^a-zA-Z0-9]", "");
    String padded = (sanitized + "0000000000000000000000").substring(0, 22);
    return MERCHANT_WALLET_PREFIX + padded;  // "0xMerchantL2Wallet" + 22 Zeichen
}
```

**Wichtige Punkte**
- Biometrie-Validierung ist ein Stub — in Prod Token kryptografisch prüfen
- Merchant-Wallet-Ableitung ist deterministisch: kein Lookup nötig

---

## Common — Domänenübergreifend

---

### UC-21 · Kontostand abfragen `[Geändert 2026-08-17]`

> **Änderungen:** Ownership-Check (403 bei Fremdzugriff) + Balance-Widget im Frontend

**Summary**
Gibt EUR-Guthaben (aus Core Banking) und USDC/EURC-Guthaben (von Circle)
für eine IBAN in einer Response zurück. Nur der Account-Inhaber darf seine eigene IBAN abfragen.

**Fachliche Einordnung**
- Einziger Endpunkt der beide Welten (Fiat + Stablecoin) vereint
- **[NEU]** Ownership-Check: `auth.getName()` → `findByCustomerId()` → `account.getIban().equals(iban)` → sonst 403 AUTH_001
- **[NEU]** Frontend: `TransferListComponent` ruft `getAccountBalance(iban)` in `ngOnInit()` auf und zeigt Balance-Widget
- In Prod: Wallet-ID aus Account-Record; derzeit vereinfacht mit `BANK_MASTER_WALLET_ID`

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `CommonController.getBalance()` | GET /accounts/{iban}/balance |
| `CoreBankingClient` | EUR-Guthaben |
| `CircleWalletClient` | USDC/EURC-Guthaben |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/accounts/{iban}/balance
  ▼
CommonController.getBalance()
  │
  ├── AccountRepository.findByIban(iban) → 404 wenn nicht gefunden
  ├── CoreBankingClient.getAccountBalance(iban)   → EUR-Betrag
  ├── CircleClient.getWalletBalance("BANK_MASTER_WALLET_ID")
  │     └── Mock: USDC + EURC Balance
  ├── balances[] → Map<currency, amount>
  └── ← 200 AccountBalanceResponse
        {iban, balanceEur, stablecoinBalances: {USDC: "...", EURC: "..."}}
```

**Code-Schnipsel**

```java
@GetMapping("/accounts/{iban}/balance")
public ResponseEntity<AccountBalanceResponse> getBalance(
        @PathVariable String iban, Authentication auth) {
    accountRepository.findByIban(iban)
        .orElseThrow(() -> new NoSuchElementException("Account not found: " + iban));

    var fiatBalance = coreBankingClient.getAccountBalance(iban);

    CircleWalletBalanceDto walletBalance =
        circleWalletClient.getWalletBalance("BANK_MASTER_WALLET_ID");

    Map<String, String> stablecoinBalances = walletBalance.balances().stream()
        .collect(Collectors.toMap(
            CircleWalletBalanceDto.Balance::currency,
            CircleWalletBalanceDto.Balance::amount));

    return ResponseEntity.ok(
        new AccountBalanceResponse(iban, fiatBalance.balanceEur(), stablecoinBalances));
}
```

**Wichtige Punkte**
- Business-Logik direkt im Controller — Ausreißer in der Schichtenarchitektur
  (alle anderen Endpunkte delegieren an Services)
- Kein Caching: jede Abfrage ruft beide externen Systeme frisch ab

---

### UC-22 · Transaktion abrufen (Cross-Domain) `[Geändert 2026-08-17]`

> **Änderungen:** Ownership-Check (403 wenn TX nicht dem JWT-User gehört)

**Summary**
Einzelne Transaktion per UUID abrufbar — unabhängig ob B2B oder B2C.
Gibt vollständige Details inkl. Status-Timeline aus dem AuditLog zurück.
Nur der Inhaber der Transaktion darf sie abrufen.

**Fachliche Einordnung**
- Einzige domänenagnostische TX-Abfrage
- `requiresApproval`-Flag: prüft ob ein `ApprovalWorkflow` existiert
- Timeline-Logik identisch zu UC-03
- **[NEU]** Ownership-Check: `tx.getCustomerAccount().getId().equals(requestingAccount.getId())` → sonst 403 AUTH_001

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `CommonController.getTransaction()` | GET /transactions/{id} |
| `StablecoinTransactionRepository` | TX laden |
| `ApprovalWorkflowRepository` | requiresApproval prüfen |
| `AuditLogRepository` | Timeline aufbauen |

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/transactions/{id}
  ▼
CommonController.getTransaction()
  │
  ├── TxRepository.findById(UUID) → 404 wenn nicht gefunden
  ├── ApprovalRepository.findByTransactionId() → requiresApproval flag
  ├── buildTimeline(txId)
  │     ├── AuditLog.findByEntityTypeAndEntityIdOrderByTimestamp()
  │     ├── Pro Eintrag: Regex auf newState → "status":"XXX"
  │     ├── TransactionStatus.valueOf(match)
  │     └── LinkedHashMap → geordnete Timeline ohne Duplikate
  └── ← 200 TransactionResponse
        {id, type, status, amountFiat, amountStablecoin, currency,
         blockchainHash, grossRevenue, requiresApproval,
         createdAt, settledAt, timeline[]}
```

**Code-Schnipsel**

```java
@GetMapping("/transactions/{id}")
public ResponseEntity<TransactionResponse> getTransaction(
        @PathVariable UUID id, Authentication auth) {
    StablecoinTransaction tx = txRepository.findById(id)
        .orElseThrow(() -> new NoSuchElementException("Transaction not found: " + id));

    boolean requiresApproval = approvalRepository.findByTransactionId(id).isPresent();

    return ResponseEntity.ok(new TransactionResponse(
        tx.getId(), tx.getType(), tx.getStatus(),
        tx.getAmountFiat(), tx.getAmountStablecoin(), tx.getCurrency(),
        tx.getBlockchainHash(), tx.getGrossRevenue(), requiresApproval,
        tx.getCreatedAt(), tx.getSettledAt(),
        buildTimeline(tx.getId())
    ));
}
```

**Wichtige Punkte**
- Kein Ownership-Check: kein Prüfen ob die TX wirklich dem anfragenden User gehört —
  in Prod muss hier eine Autorisierungsprüfung rein
- `buildTimeline()` ist Code-Duplikat zu `B2bTransferService.buildTimeline()` —
  könnte in eine gemeinsame Utility-Klasse extrahiert werden

---

---

## Neue Use Cases (2026-08-17)

---

### UC-23 · Nachtlicher Sanctions-Batch `[NEU 2026-08-17]`

**Summary**
Täglich um 02:00 Uhr scannt ein Scheduler alle ACTIVE-Adressbucheinträge gegen Chainalysis.
Neu-klassifizierte HIGH_RISK-Adressen werden auf REVOKED gesetzt und via n8n gemeldet.
Manuell auslösbar über einen Admin-Endpunkt.

**Fachliche Einordnung**
- FATF/MiCA: Sanktionslisten (OFAC SDN, EU-Konsolidierte Liste) werden täglich aktualisiert
- Eine heute LOW_RISK-Adresse kann morgen auf der OFAC-Liste stehen
- Fail-safe: Ein Fehler bei einer Adresse bricht nicht den gesamten Batch ab
- n8n-Benachrichtigung (best effort): Netzwerkfehler werden geloggt, nicht propagiert
- Manueller Trigger für Tests/Notfall: `POST /api/v1/b2b/admin/sanctions-scan`

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `SanctionsBatchService` | `@Scheduled(cron = "0 0 2 * * ?")`, Hauptlogik |
| `AddressBookRepository.findByStatus(ACTIVE)` | Alle aktiven Einträge laden |
| `ChainalysisClient` | Screening pro Adresse |
| `AuditLogRepository` | INSERT `SANCTIONS_BATCH_REVOKED` |
| `N8nWebhookClient.notifyAddressRevoked()` | Kundenmeldung (best effort) |
| `B2bController.triggerSanctionsScan()` | Admin-Endpunkt manueller Trigger |

**Sequenzdiagramm**

```
Scheduler (täglich 02:00) ODER Admin
  │  POST /api/v1/b2b/admin/sanctions-scan (manueller Trigger)
  ▼
SanctionsBatchService.runNightlySanctionsScan()
  │
  ├── AddressBookRepository.findByStatus(ACTIVE) → alle aktiven Einträge
  │
  └── Pro AddressBook-Eintrag: screenAndRevokeIfHighRisk()
        ├── ChainalysisClient.screenAddress(wallet, currency, POLYGON, outgoing)
        │
        ├─ [approved=true] → weiter (nichts tun)
        │
        └─ [approved=false / HIGH_RISK]
              ├── DB: address.status = REVOKED
              ├── DB: AuditLog SANCTIONS_BATCH_REVOKED (entityId=addressId, userId="SYSTEM")
              ├── N8nClient.notifyAddressRevoked(wallet, customerId, riskScore) [best effort]
              └── log.warn REVOKED

  └── log.info "Scan complete. revoked=X/Y"
```

**Code-Schnipsel**

```java
@Scheduled(cron = "0 0 2 * * ?")
@Transactional
public void runNightlySanctionsScan() {
    List<AddressBook> active = addressBookRepository.findByStatus(AddressStatus.ACTIVE);
    log.info("[SANCTIONS-BATCH] Starting scan for {} active addresses", active.size());
    int revoked = 0;
    for (AddressBook address : active) {
        try {
            if (screenAndRevokeIfHighRisk(address)) revoked++;
        } catch (Exception e) {
            log.error("[SANCTIONS-BATCH] Error screening address={}: {}",
                address.getWalletAddress(), e.getMessage());
        }
    }
    log.info("[SANCTIONS-BATCH] Done. revoked={}/{}", revoked, active.size());
}
```

**Wichtige Punkte**
- `@EnableScheduling` war bereits in `StablecoinApplication` aktiv — kein neues Setup nötig
- Der Admin-Endpunkt `POST /admin/sanctions-scan` ist nicht per IAM gesichert — in Prod sollte er ROLE_ADMIN brauchen
- AuditLog-`userId` = `"SYSTEM"` (kein authentifizierter User im Scheduler-Context)

---

### UC-24 · Institutionelle Adresse hinzufügen `[NEU 2026-08-17]`

**Summary**
Bank-Administrator fügt eine regulierte Gegenpartei (Coinbase Custody, Kraken etc.) zur
bank-weiten institutionellen Whitelist hinzu. Chainalysis-Screen ist Pflicht.

**Fachliche Einordnung**
- Bank-weite Whitelist — KEIN `customer_account_id` FK, gilt für alle Kunden
- Vermeidet, dass jeder Kunde dieselbe Regulierte-Gegenpartei-Adresse einzeln whitelisten muss
- Chainalysis-Screen: HIGH_RISK → 403 COMPLIANCE_001 (Fail-Closed)
- Eindeutigkeitsbedingung: `UNIQUE(wallet_address, currency)` — gleiche Adresse kann nicht zweimal (pro Währung) eingetragen werden

**Beteiligte Klassen**

| Klasse | Rolle |
|---|---|
| `B2bController.addInstitutionalAddress()` | POST /institutional-address-book |
| `InstitutionalAddressBookService.addAddress()` | Chainalysis-Screen + Persist |
| `InstitutionalAddressBookRepository` | DB-Zugriff (Tabelle `institutional_address_book`) |
| `ChainalysisClient` | Screening |

**Sequenzdiagramm**

```
Admin
  │  POST /api/v1/b2b/institutional-address-book
  │  Body: {label, walletAddress, currency}
  ▼
B2bController → InstitutionalAddressBookService.addAddress() [@CircuitBreaker(chainalysis)]
  │
  ├── ChainalysisClient.screenAddress(wallet, currency, POLYGON, outgoing)
  │
  ├─ [!approved] → 403 ComplianceBlockException
  │
  └─ [approved]
        ├── DB: institutional_address_book INSERT (status=ACTIVE, riskScore=LOW/MEDIUM)
        ├── DB: AuditLog INST_ADDRESS_ADDED
        └── ← 201 InstitutionalAddressBookResponse
```

**Wichtige Punkte**
- Fail-Closed: Chainalysis-Ausfall → Adresse wird blockiert (analog `AddressBookService`)
- `created_by` wird aus `auth.getName()` gesetzt — Nachvollziehbarkeit wer die Adresse hinzugefügt hat
- Auswirkung auf UC-01: Transfer an institutionelle Adresse wird akzeptiert auch wenn sie nicht im Kunden-Adressbuch steht

---

### UC-25 · Institutionelle Adressen auflisten `[NEU 2026-08-17]`

**Summary**
Liste aller ACTIVE-Einträge der bank-weiten institutionellen Whitelist.

**Sequenzdiagramm**

```
Client
  │  GET /api/v1/b2b/institutional-address-book
  ▼
B2bController → InstitutionalAddressBookService.listAddresses() [readOnly]
  │
  ├── InstitutionalAddressBookRepository.findByStatus(ACTIVE)
  └── ← 200 List<InstitutionalAddressBookResponse>
```

**Wichtige Punkte**
- Keine Kunden-Filterung — alle ACTIVE-Einträge werden zurückgegeben (bank-weit)
- REVOKED-Einträge werden gefiltert (Soft-Delete, DB-Eintrag bleibt für Audit)

---

### UC-26 · Institutionelle Adresse widerrufen `[NEU 2026-08-17]`

**Summary**
Soft-Delete einer institutionellen Whitelist-Adresse. Status → `REVOKED`.
Ab sofort können keine Transfers mehr an diese Adresse initiiert werden (sofern sie nicht im Kunden-Adressbuch steht).

**Sequenzdiagramm**

```
Admin
  │  DELETE /api/v1/b2b/institutional-address-book/{id}
  ▼
B2bController → InstitutionalAddressBookService.revokeAddress(id, userId) [@Transactional]
  │
  ├── InstitutionalAddressBookRepository.findById(id) → 404 wenn nicht gefunden
  ├── DB: address.status = REVOKED
  ├── DB: AuditLog INST_ADDRESS_REVOKED
  └── ← 204 No Content
```

**Wichtige Punkte**
- Sofortige Wirkung: Der nächste Transfer an diese Adresse schlägt mit 403 fehl (sofern nicht im Kunden-Adressbuch)
- Kein physisches Löschen — Eintrag bleibt in DB für Audit Trail

---

## Zusammenfassung — Bemerkenswerte Querschnittsthemen

### 1. Self-Injection für REQUIRES_NEW (UC-01, UC-04)

```java
@Lazy @Autowired
private B2bTransferService self;
```

Transaktionen die nach einem Fehler committed bleiben müssen (Status-Update auf `FAILED`/`BLOCKED`)
laufen in `@Transactional(REQUIRES_NEW)`. Da Spring-AOP nur über den Proxy-Aufruf greift, muss
der Service sich selbst über den Proxy aufrufen — direktes `this.` würde den Mechanismus umgehen.
`@Lazy` verhindert den zirkulären Dependency-Fehler beim Start.

### 2. Transactional Outbox Pattern

Jede Statusänderung schreibt parallel in `outbox_message`. Diese Tabelle ist die Grundlage für
zuverlässige Event-Delivery an Downstream-Systeme — kein direkter Kafka/RabbitMQ-Call der
verloren gehen könnte.

### 3. AuditLog als Append-only Event-Store

Die `audit_log`-Tabelle ist INSERT-only und dient als Event-Store für den kompletten Lebenszyklus
jeder TX. Die Status-Timeline (UC-03, UC-22) wird daraus per Regex-Parsing rekonstruiert —
kein separates Timeline-Feld in der TX-Tabelle nötig.

### 4. Circuit Breaker Fail-Closed (UC-07, UC-01)

Chainalysis-Ausfall führt nicht zu einem "alles erlaubt"-Fallback, sondern zu einem Block.
Entscheidend: `ComplianceBlockException` muss als `ignore-exception` konfiguriert sein —
sonst öffnet der Breaker nach jeder legitimen HIGH_RISK-Ablehnung.

### 5. Privacy-by-Design bei Phone-Aliases (UC-14, UC-15)

Telefonnummern werden niemals im Klartext gespeichert — nur `SHA-256(Salt + Nummer)`.
Der Salt `"atruvia-stablecoin-2026"` muss bei manuellen DB-Inserts exakt stimmen.

### 6. FAILED als Redeem-Marker (UC-17)

`TransactionStatus.FAILED` wird als interner Status für aufgelöste Yield-Positionen verwendet.
`getPosition()` sucht nur `SETTLED`-Einträge — nach dem Redeem verschwindet die Position
automatisch. Ungewöhnlich, da `FAILED` sonst echte Fehler bedeutet, aber in HANDOVER.md
dokumentiert.

### 7. Keine REQUIRES_NEW bei B2C-Services (UC-13, UC-14, UC-16, UC-20)

B2C-Services sind einfacher strukturiert: alles läuft in einer einzigen `@Transactional`.
Wenn der Circle-Call fehlschlägt, wird der gesamte DB-Write zurückgerollt. Das ist möglich,
weil B2C-Flows keine externen Statusphasen haben die committed bleiben müssen.

### 8. Determinismus statt Lookup (UC-20)

Merchant-Wallets werden deterministisch aus der Merchant-ID abgeleitet —
kein DB-Lookup, keine Registrierung nötig. Gleiche Merchant-ID → immer gleiche Wallet-Adresse.
