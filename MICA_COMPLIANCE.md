# MiCA-Compliance-Dokumentation
## Atruvia AG — Stablecoin-Zahlungsplattform

**Dokumentenversion:** 1.0  
**Stand:** August 2026  
**Verantwortlich:** Atruvia AG, Bereich Zahlungsverkehr & Custody

---

### 1. Regulatorischer Rahmen

| Aspekt | Ausprägung |
|---|---|
| Verordnung | Markets in Crypto-Assets Regulation (MiCA), EU 2023/1114 |
| Geltung ab | 30. Dezember 2024 (Titel III + IV) |
| Geltungsbereich | Emission und Handel von E-Geld-Token (EMT) |
| Eingesetzte Token | **USDC** (USD Coin) — USD-E-Geld-Token, **EURC** (Euro Coin) — EUR-E-Geld-Token |
| Token-Emittent | Circle Internet Financial, LLC (MiCA-lizenzierter E-Geld-Token-Anbieter) |
| Plattformrolle | Atruvia AG als technischer Intermediär (Zahlungsdienstleister gem. PSD2) |
| Netzwerk | Polygon-Blockchain (MATIC) als Settlement-Layer |

Die Plattform wickelt ausschließlich Token von Circle Internet Financial ab, das als zugelassenes
E-Geld-Institut die Reservehaltung, Einlösepflichten und MiCA-Artikel 50 ff. (White Paper,
Rückkaufrecht, Reservetransparenz) unmittelbar verantwortet.

---

### 2. AML/KYC-Kontrollen

#### 2.1 KYC-Tier-System

Jedes Kundenkonto (`customer_account`) trägt einen unveränderlichen KYC-Tier, der vom Onboarding-Prozess
des Kernbankensystems gesetzt wird:

| Tier | Einzel-TX-Limit (Standard) | Tageslimit | Typischer Kundenkreis |
|---|---|---|---|
| `TIER_1` | 1.000 EUR | 2.500 EUR | Privatkunden ohne erweitertes KYC |
| `TIER_2` | 5.000 EUR | 10.000 EUR | Privatkunden mit Identitätsprüfung |
| `TIER_3` | 500.000 EUR | 2.000.000 EUR | Firmenkunden mit vollständiger AML-Prüfung |

Die Limits werden per DB-Spalten (`tx_limit_single`, `tx_limit_daily`) pro Konto individuell gesetzt
und können nicht durch API-Parameter überschrieben werden.

#### 2.2 On-Chain-Adress-Screening (Chainalysis)

Vor jeder ausgehenden Transaktion ruft `ComplianceService.screenAndAssert()` die Chainalysis-API auf:

```
POST chainalysis /api/v1/address/screen
{
  "address": "<Ziel-Wallet>",
  "asset": "USDC",
  "network": "POLYGON",
  "direction": "outgoing"
}
```

**Ergebnis-Verarbeitung:**
- `approved = false` → sofortiger `ComplianceBlockException`, Transaktion erhält Status `BLOCKED`
- `sanctionedEntity = true` → immer `BLOCKED`, auch bei Chainalysis-Ausfall (Fail-Closed via Circuit-Breaker-Fallback)
- Jedes Screening-Ergebnis wird als `COMPLIANCE_SCREEN` oder `COMPLIANCE_BLOCKED` im `audit_log` persistent gespeichert

**Circuit-Breaker (Resilience4j):**  
Bei Chainalysis-Unavailability greift `screenAddressFallback()`, das die Transaktion aus Vorsicht
ebenfalls blockiert (`COMPLIANCE_FALLBACK_BLOCK`) — kein Durchlassen bei API-Ausfall.

#### 2.3 Compliance-Block-Automatismus

```
ComplianceService
  └── screenAndAssert(address, txId, userId)
        ├── COMPLIANCE_SCREEN → AuditLog gespeichert
        ├── approved=false → COMPLIANCE_BLOCKED → AuditLog gespeichert
        │                 → ComplianceBlockException geworfen
        └── CircuitBreaker-Fallback → COMPLIANCE_FALLBACK_BLOCK → Exception
```

---

### 3. Transaktionslimits und Monitoring

#### 3.1 Taurus Custody — Single-TX-Limit

In `application.yml` ist das Taurus-Einzel-TX-Limit konfiguriert:

```yaml
app:
  taurus:
    single-tx-limit: 1000000.00  # 1.000.000 EUR
```

`MockTaurusCustodyClient` (Dev) und `HttpTaurusCustodyClient` (Prod) werfen bei Überschreitung
eine `TaurusLimitExceededException`, die in `B2bTransferService.executeTransferFlow()` abgefangen
und als `FAILED`-Transaktion mit Fehlergrund persistiert wird.

#### 3.2 Vier-Augen-Prinzip (Approval Workflow)

Transaktionen, die das kundeneigene Einzel-Limit (`tx_limit_single`) überschreiten, werden nicht
sofort verarbeitet, sondern in einen `ApprovalWorkflow` überführt:

```
amountEur > account.txLimitSingle
  → Status: AWAITING_APPROVAL
  → ApprovalWorkflow erstellt (24h Gültigkeitsfenster)
  → APPROVAL_REQUIRED im AuditLog
```

Erst nach explizitem `POST /api/v1/b2b/transfers/{id}/approve` durch einen zweiten Nutzer
(`approverId ≠ initiatorId` — in Produktion durch IAM sicherzustellen) wird der Transfer-Flow
ausgeführt. Die Vier-Augen-Prüfung ist damit strukturell im Code verankert.

#### 3.3 Transaktionsstatus-Monitoring

Alle Statusübergänge einer Transaktion werden als unveränderliche `AuditLog`-Einträge gespeichert
und sind über `GET /api/v1/b2b/transfers/{id}` als `timeline`-Array abrufbar. Über
`GET /api/v1/b2b/transfers?status=BLOCKED` können alle blockierten Transaktionen abgefragt werden.

---

### 4. Revisionssicherheit und Audit-Trail

#### 4.1 INSERT-Only AuditLog

Die Tabelle `audit_log` ist als reines Append-Log konzipiert:

- `AuditLogRepository` stellt ausschließlich `save()` (INSERT) und Leseoperationen bereit —
  keine `delete()`- oder Update-Methoden
- Einträge enthalten: `entity_type`, `entity_id`, `action`, `previous_state` (JSON),
  `new_state` (JSON), `user_id`, `ip_address`, `trace_id`, `timestamp`
- In Produktion ist die DB-Rolle der Anwendung auf INSERT/SELECT für `audit_log` zu beschränken
  (kein DELETE/UPDATE-Privileg)

Gespeicherte Aktionen: `CREATED`, `STATUS_CHANGED`, `COMPLIANCE_SCREEN`, `COMPLIANCE_BLOCKED`,
`COMPLIANCE_FALLBACK_BLOCK`, `APPROVAL_REQUIRED`, `APPROVED`, `REJECTED`, `SETTLED`, `FAILED`,
`COMPLIANCE_BLOCKED`, `P2P_SENT`, `PHONE_ALIAS_REGISTERED`.

#### 4.2 OpenTelemetry-Tracing

`application.yml` aktiviert vollständiges Tracing:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

Alle HTTP-Requests und Service-Calls werden mit Trace-ID instrumentiert. Die Trace-ID wird im
`AuditLog.traceId`-Feld und per MDC (`userId`) in alle Log-Ausgaben eingebettet.

#### 4.3 Outbox-Pattern (Transaktionale Nachrichten)

Geschäftsereignisse werden atomar mit der Haupttransaktion in die `outbox_message`-Tabelle
geschrieben (`OutboxMessage`). `OutboxProcessor` verarbeitet sie asynchron weiter — garantierte
At-Least-Once-Delivery ohne verteilte Transaktionen.

Ereignistypen: `TRANSACTION_INITIATED`, `TRANSACTION_SETTLED`, `TRANSACTION_FAILED`,
`TRANSACTION_BLOCKED`.

---

### 5. Reporting und Transparenz

#### 5.1 ISO 20022 CAMT.053-Export

`GET /api/v1/b2b/export/camt053` liefert alle `SETTLED`-Transaktionen eines IBAN als
CAMT.053.001.08-konformes XML. Der Export enthält Blockchain-Hash (`<AddtlNtryInf>`) und
Circle-Transaction-ID als maschinenlesbare Nachweise.

#### 5.2 DATEV-Export

`GET /api/v1/b2b/export/datev` exportiert Transaktionen als DATEV-kompatibles CSV für die
Finanzbuchhaltung. Das Format folgt dem DATEV-Buchungsstapel-Standard (Konto, Gegenkonto,
Betrag EUR, Belegtext).

#### 5.3 Blockchain-Hash-Nachweis

Nach Settlement wird der On-Chain-Transaktions-Hash (`transactionHash` aus Circle API) in
`stablecoin_transaction.blockchain_hash` gespeichert und ist über die REST-API (`TransactionResponse`)
und den CAMT.053-Export abrufbar. Dieser Hash ermöglicht die unabhängige Verifikation auf dem
Polygon-Block-Explorer.

#### 5.4 Revenue-Transparenz

Jede Transaktion speichert: `fx_spread`, `transaction_fee`, `gas_cost`, `gross_revenue` —
reproduzierbar aus `RevenueService` nachrechenbar. Konfiguration in `application.yml`:

```yaml
app:
  revenue:
    fx-spread: 0.0015    # 0,15 % FX-Spread
    fee-b2b: 2.50        # 2,50 EUR B2B-Festgebühr
    fee-b2c: 0.50        # 0,50 EUR B2C-Festgebühr
    gas-cost-simulated: 0.008
```

---

### 6. Datenschutz (DSGVO-Konformität)

#### 6.1 SHA-256-Hashing von Telefonnummern

Telefonnummern werden ausschließlich als SHA-256-Hash mit festem Salt gespeichert — kein
Plaintext kommt in die Datenbank:

```java
// B2cP2pService.java
private static final String PHONE_SALT = "atruvia-stablecoin-2026";

private String hashPhoneNumber(String phoneNumber) {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] salted = (PHONE_SALT + phoneNumber).getBytes(StandardCharsets.UTF_8);
    // → 64-stelliger Hex-String in phone_alias.phone_number_hash
}
```

Spalte: `phone_alias.phone_number_hash VARCHAR(64) UNIQUE NOT NULL` — keine Rückrechnung möglich.

#### 6.2 Keine Plaintext-PII in Logs

- Log-Ausgaben enthalten ausschließlich `customer_id` (opake Kennung), `tx_id` (UUID) und
  Wallet-Adressen (Pseudonyme)
- `AuditLog.newState` enthält Wallet-Adressen und Risk-Scores, aber keine Klarnamen,
  IBAN-Vollnummern oder Telefonnummern
- MDC-Schlüssel `userId` trägt die opake `customer_id` (`cust-b2b-001`), keine personenbezogenen Daten

#### 6.3 SSL/TLS in Produktion

`application-prod.yml` erzwingt SSL für Datenbankverbindungen:

```yaml
spring:
  datasource:
    hikari:
      ssl: true
      ssl-mode: require
```

Für mTLS zwischen Diensten ist `SSL_KEYSTORE_PATH` / `SSL_TRUSTSTORE_PATH` per Umgebungsvariable
zu konfigurieren (siehe `.env.example`).

#### 6.4 Datenminimierung

- B2B-Transaktionen speichern keine Empfängerpersonendaten — nur Ziel-Wallet-Adresse
- B2C-P2P-Transfers nutzen ausschließlich den Telefonnummer-Hash als Lookup-Schlüssel
- CAMT.053 und DATEV-Exporte enthalten keine Telefonnummern

---

*Dieses Dokument ist Bestandteil des internen Compliance-Portfolios der Atruvia AG.  
Änderungen bedürfen der Freigabe durch Compliance & Legal.*
