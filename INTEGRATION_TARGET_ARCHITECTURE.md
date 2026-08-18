# Zielarchitektur: Async Event-Driven Microservices
## Atruvia Stablecoin Integration Platform — Cloud-Native Target Architecture

> **Version:** 1.0 | **Datum:** 2026-08-18  
> **Autor:** Principal Cloud-Native & Data-Mesh Architect, Atruvia AG  
> **Status:** Konzept + Dev-Simulation implementiert | Prod-Migration: Q4 2026  
> **Prinzip:** Kein Mainframe, kein COBOL, kein MQ-Series, kein Avro

---

## 1. Executive Summary — Warum dieser Umbau?

Die aktuelle Plattform nutzt **synchrone HTTP-Aufrufe** (CircleWalletClient, TaurusCustodyClient, CoreBankingClient, ComplianceService), die folgende Probleme erzeugen:

| Problem | Auswirkung |
|---|---|
| Synchrones Chainalysis-Screening blockiert TX-Thread | Latenz 300–800ms je TX, schlechte Skalierung |
| CAMT.053/DATEV im HTTP-Response-Stream | Kein Retry, keine Langzeitarchivierung, schlechte UX |
| Direkte PostgreSQL-Abfragen für BI/DWH | RLS-Verletzungsrisiko, keine Datenproduktentkopplung |
| GENO pago ZS als CoreBanking | Mainframe-Abhängigkeit, MQ-Series, COBOL |

**Zielbild:** Vollständig ereignisgesteuerte Architektur auf Apache Kafka (JSON), S3-basierter Export und versionierte Data-Mesh-Datenprodukte.

---

## 2. Systemarchitektur — Zielzustand

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ATRUVIA STABLECOIN PLATFORM (Spring Boot 3)              │
│                                                                             │
│  REST API ──► B2bTransferService ──► Kafka Producer (JSON)                 │
│                     │                       │                               │
│                     │         ┌─────────────▼──────────────────────────┐   │
│                     │         │  Apache Kafka Cluster (3 Broker)        │   │
│                     │         │  ┌──────────────────────────────────┐  │   │
│                     │         │  │ stablecoin-transfers              │  │   │
│                     │         │  │ compliance-screening              │  │   │
│                     │         │  │ yield-lifecycle                   │  │   │
│                     │         │  │ stablecoin-analytics-v1 (RO)     │  │   │
│                     │         │  └──────────────────────────────────┘  │   │
│                     │         └─────────────────────────────────────────┘   │
│                     │                       │                               │
│              ┌──────▼──────┐    ┌───────────▼───────────┐                  │
│              │  PostgreSQL  │    │  Kafka Consumer Groups │                  │
│              │  (RLS aktiv) │    │  - ledger-service      │                  │
│              └─────────────┘    │  - compliance-processor │                  │
│                                 │  - notification-service │                  │
│                                 │  - analytics-sink       │                  │
│                                 └───────────┬───────────  │                  │
│                                             │                               │
│  Export Trigger ──► S3 Upload ──► Presigned URL          │                  │
│  (POST /export/async-trigger)   (15min TTL)               │                  │
│                                                           │                  │
│  GET /api/v1/analytics/** ────────────────────────────────┘                  │
│  (Data-Mesh Datenprodukt, RLS-isoliert)                                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Kafka-Topics — JSON-Schemata (kein Avro, keine Schema-Registry)

### 3.1 Topic: `stablecoin-transfers`

**Zweck:** Jeder Statuswechsel einer Stablecoin-Transaktion — Quelle für Ledger, Notifications, Audit.  
**Key:** `transactionId` (für Kafka-Partitionierung nach TX-ID)  
**Retention:** 30 Tage  
**Partitions:** 12 (skaliert auf ~10.000 TPS)

```json
{
  "eventId":          "550e8400-e29b-41d4-a716-446655440000",
  "eventType":        "TRANSFER_STATUS_CHANGED",
  "schemaVersion":    "1.0",
  "topic":            "stablecoin-transfers",
  "timestamp":        "2026-08-18T10:00:00.123Z",
  "tenantId":         "tenant-kleine-vb",
  "userId":           "cust-b2b-001",
  "transactionId":    "e4b2d5a3-7691-4f9e-a89d-7422f6d2f33c",
  "transactionType":  "OUTBOUND",
  "previousStatus":   "FUNDS_HELD",
  "currentStatus":    "SUBMITTED",
  "amountFiat":       10000.00,
  "amountStablecoin": 10823.00,
  "currency":         "USDC",
  "blockchainHash":   null,
  "grossRevenue":     null,
  "sourceIban":       "DE89370400440532013000"
}
```

**Mögliche `currentStatus`-Werte:**
`CREATED` → `COMPLIANCE_CHECKED` → `FUNDS_HELD` → `SUBMITTED` → `SETTLED`  
oder `FAILED`, `REJECTED`, `EXPIRED`, `INBOUND_RETURN`, `RETURNED`, `UNASSIGNED`

**Consumer-Groups:**

| Consumer | Aktion |
|---|---|
| `ledger-service` | Bucht TX in Hauptbuch (ersetzt CoreBankingClient-Synchronaufruf) |
| `notification-service` | Sendet E-Mail/Push bei SETTLED, FAILED |
| `audit-service` | Persistiert in unveränderlichem Audit-Store (S3/Lakehouse) |
| `outbox-cleaner` | Markiert OutboxMessages als SENT |

---

### 3.2 Topic: `compliance-screening`

**Zweck:** Entkopplung des Chainalysis-AML-Screenings.  
**Key:** `correlationId` (= transactionId)  
**Retention:** 7 Tage  
**Partitions:** 6

**Request-Event (Platform → Chainalysis-Consumer):**
```json
{
  "eventId":         "a1b2c3d4-0000-0000-0000-000000000001",
  "eventType":       "SCREENING_REQUESTED",
  "schemaVersion":   "1.0",
  "topic":           "compliance-screening",
  "timestamp":       "2026-08-18T10:00:00Z",
  "tenantId":        "tenant-kleine-vb",
  "correlationId":   "e4b2d5a3-7691-4f9e-a89d-7422f6d2f33c",
  "walletAddress":   "0xA100000000000000000000000000000000000001",
  "direction":       "outgoing",
  "userId":          "cust-b2b-001"
}
```

**Result-Event (Chainalysis-Consumer → Platform):**
```json
{
  "eventId":         "a1b2c3d4-0000-0000-0000-000000000002",
  "eventType":       "SCREENING_COMPLETED",
  "schemaVersion":   "1.0",
  "topic":           "compliance-screening",
  "timestamp":       "2026-08-18T10:00:01Z",
  "tenantId":        "tenant-kleine-vb",
  "correlationId":   "e4b2d5a3-7691-4f9e-a89d-7422f6d2f33c",
  "walletAddress":   "0xA100000000000000000000000000000000000001",
  "direction":       "outgoing",
  "userId":          "SYSTEM",
  "result":          "APPROVED",
  "riskScore":       "LOW",
  "reason":          null
}
```

**Migration-Pattern:** Saga-Orchestration
- Platform sendet `SCREENING_REQUESTED` und wartet auf `SCREENING_COMPLETED` (Korrelation via `correlationId`)
- Timeout nach 30 Sekunden → automatischer Fallback auf `COMPLIANCE_REJECTED`
- Chainalysis-Consumer läuft als eigenständiger Microservice (kein Monolith-Coupling)

---

### 3.3 Topic: `yield-lifecycle`

**Zweck:** Yield-Sparkonto-Ereignisse für Steuer-Reporting (AtruviaTaxClient), BaFin-Meldewesen, Jahresabschluss.  
**Key:** `positionId`  
**Retention:** 365 Tage (steuerrechtliche Aufbewahrungspflicht)  
**Partitions:** 4

**YIELD_DEPOSIT_CREATED:**
```json
{
  "eventId":          "uuid",
  "eventType":        "YIELD_DEPOSIT_CREATED",
  "schemaVersion":    "1.0",
  "topic":            "yield-lifecycle",
  "timestamp":        "2026-08-18T10:00:00Z",
  "tenantId":         "tenant-kleine-vb",
  "customerId":       "cust-b2c-001",
  "positionId":       "pos-uuid",
  "depositTxId":      "tx-uuid",
  "redeemTxId":       null,
  "principalEur":     2000.00,
  "currentValueEur":  2000.00,
  "accruedYieldEur":  0.00,
  "taxWithheldEur":   0.00,
  "netPayoutEur":     2000.00,
  "annualRatePct":    3.5,
  "daysHeld":         0,
  "status":           "ACTIVE"
}
```

**YIELD_REDEEMED (mit Steuerkomponenten):**
```json
{
  "eventType":        "YIELD_REDEEMED",
  "positionId":       "pos-uuid",
  "redeemTxId":       "tx-uuid-2",
  "principalEur":     2000.00,
  "currentValueEur":  2001.34,
  "accruedYieldEur":  1.34,
  "taxWithheldEur":   0.00,
  "netPayoutEur":     2001.34,
  "daysHeld":         7,
  "status":           "CLOSED"
}
```

---

### 3.4 Topic: `stablecoin-analytics-v1` (Data Mesh — Read-only Datenprodukt)

**Zweck:** Aggregiertes Datenprodukt für DWH, BI-Dashboards, regulatorisches Meldewesen.  
**Key:** `tenantId` (für Lakehouse-Partitionierung)  
**Retention:** 365 Tage  
**Partitions:** 4  
**Zugriff:** Read-only (kein Schreiben durch externe Konsumenten)

```json
{
  "eventId":              "uuid",
  "eventType":            "TRANSFER_SETTLED",
  "schemaVersion":        "1.0",
  "topic":                "stablecoin-analytics-v1",
  "timestamp":            "2026-08-18T10:00:03Z",
  "tenantId":             "tenant-kleine-vb",
  "transactionId":        "e4b2d5a3...",
  "transactionType":      "OUTBOUND",
  "currency":             "USDC",
  "amountFiatEur":        10000.00,
  "amountStablecoin":     10823.00,
  "fxRate":               1.0823,
  "fxSpread":             0.0015,
  "grossRevenueEur":      17.492,
  "transactionFeeEur":    2.50,
  "gasCostEur":           0.008,
  "settledAt":            "2026-08-18T10:00:03Z",
  "customerType":         "B2B",
  "kycTier":              "TIER_3"
}
```

---

## 4. S3 Objekt-Storage — Export-Struktur

### 4.1 Bucket-Struktur

```
s3://stablecoin-exports/                         ← Haupt-Bucket (Atruvia Ceph RZ)
  {tenant_id}/                                   ← Mandanten-Partition (RLS-äquivalent)
    year={yyyy}/                                 ← Hive-kompatible Zeitpartitionierung
      month={MM}/
        {exportType}-{iban}-{timestamp}.{ext}
```

**Beispiele:**
```
s3://stablecoin-exports/
  tenant-kleine-vb/year=2026/month=08/
    camt053-DE89370400440532013000-2026-08-18T10-00-00.xml
    camt054-DE89370400440532013000-2026-08-18T10-00-00.xml
    camt029-DE89370400440532013000-2026-08-18T10-00-00.xml
    datev-DE89370400440532013000-2026-08-18T10-00-00.csv
  tenant-grosse-vb/year=2026/month=08/
    camt053-DE89370400440532013002-2026-08-18T10-05-00.xml
```

### 4.2 Async-Export-Flow

```
Client                   Platform                    S3 / Ceph
  │                         │                            │
  │ POST /export/async-trigger?type=camt053              │
  │ ─────────────────────►  │                            │
  │                         │ generateCamt053(iban)       │
  │                         │ ─────────────────────────► │
  │                         │ ◄───── s3Key ─────────────  │
  │                         │ generatePresignedUrl(s3Key, 15min)
  │                         │                            │
  │ ◄── 202 Accepted ──────  │                            │
  │ { presignedUrl, 900s }  │                            │
  │                         │                            │
  │ GET {presignedUrl}                                   │
  │ ──────────────────────────────────────────────────►  │
  │ ◄────────── 200 OK + XML/CSV ──────────────────────  │
```

### 4.3 Presigned URL Security

- **Gültigkeit:** 15 Minuten (konfigurierbar via `app.exports.presigned-url-ttl-seconds`)
- **Mandanten-Isolation:** S3-Key enthält `tenant_id` als Präfix; IAM/Ceph-Policy verhindert Cross-Tenant-Zugriff
- **Dev-Simulation:** Token = `Base64(s3Key + "|" + expiresEpoch)`; Served by `DevExportDownloadController`
- **Prod:** AWS S3 `presignedGetObjectRequest()` oder Ceph RadosGW equivalent

---

## 5. Data Mesh — Datenprodukt "Zahlungsverkehr-Analytics"

### 5.1 Design-Prinzipien

```
┌──────────────────────────────────────────────────────────────────────┐
│                     DATA MESH — OWNERSHIP                             │
│                                                                       │
│  Data Owner: Stablecoin-Platform-Team (dieser Service)               │
│  Data Product: "Zahlungsverkehr-Analytics" v1.0                      │
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────┐     │
│  │  INTERN (geschützt)          │  DATENPRODUKT (exponiert)    │     │
│  │  ─────────────────           │  ──────────────────────────  │     │
│  │  PostgreSQL (RLS aktiv)      │  REST API v1 (read-only)     │     │
│  │  stablecoin_app user         │  GET /api/v1/analytics/**    │     │
│  │  TX-Tabellen                 │                              │     │
│  │                              │  Kafka Topic (read-only)     │     │
│  │  KEIN direkter Zugriff       │  stablecoin-analytics-v1     │     │
│  │  für externe Konsumenten!    │                              │     │
│  └─────────────────────────────────────────────────────────────┘     │
│                                                                       │
│  RLS-Isolation: tenant_id immer in jedem Analytics-Record eingebettet │
│  → Lakehouse-Partitionierung nach tenant_id (kein Cross-Tenant-Leak)  │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.2 RLS-Isolation im Datenprodukt

```
JWT {tenant: "tenant-kleine-vb"}
  ↓
TenantContext.set("tenant-kleine-vb")
  ↓
Analytics Controller → txRepository.find* (RLS aktiv, stablecoin_app)
  ↓ nur tenant-kleine-vb Daten sichtbar
Kafka Analytics Event → tenantId = "tenant-kleine-vb" eingebettet
  ↓
Lakehouse Partition: /data/tenant-kleine-vb/...
  ↓ Atruvia IAM Policy: nur tenant-kleine-vb Benutzer können lesen
```

### 5.3 API-Endpunkte (versioniert)

```
GET /api/v1/analytics/summary    → Monats-Zusammenfassung (Count, Volume, Revenue)
GET /api/v1/analytics/revenue    → Ertragskomponenten für MaRisk-Reporting
```

### 5.4 Lakehouse-Integration (Atruvia Data Platform)

```
Kafka Consumer (analytics-sink)
  │ konsumiert stablecoin-analytics-v1
  ▼
Apache Spark / Flink (Streaming ETL)
  │ Schreibt Parquet-Dateien
  ▼
S3 / Ceph Lakehouse
  s3://atruvia-lakehouse/stablecoin/
    tenant_id=tenant-kleine-vb/
      year=2026/month=08/day=18/
        transfers_*.parquet
  │
  ▼
Apache Iceberg / Delta Lake (ACID-Tabellen)
  │
  ▼
BI-Tools (Tableau, PowerBI, Metabase)
  Nur mandantenspezifische Views sichtbar (Row-Level-Security im Lakehouse)
```

---

## 6. GAP-Analyse: Aktueller Code → Zielzustand

### 6.1 B2bTransferService

| Zeile | Aktuell (synchron) | Zielbild (async) | Aufwand |
|---|---|---|---|
| ~500 | `complianceService.screenAndAssert()` (blocking) | `kafkaProducer.publishComplianceRequest()` + Saga-Wait | **Hoch** |
| ~510 | `coreBankingClient.createHold()` (blocking) | Kafka Event `FUNDS_HELD_REQUESTED` → CoreBanking-Consumer | **Mittel** |
| ~540 | `circleWalletClient.initiateTransfer()` (HTTP) | Bereits resilient via Retry/CB; optional Kafka-basiert | **Niedrig** |
| ~560 | `coreBankingClient.createLedgerBooking()` (blocking) | Kafka Event `SETTLED` → Ledger-Consumer | **Mittel** |

**✅ Bereits implementiert:** `TransferStatusEvent` wird bei jedem `transitionTo()` publiziert.

### 6.2 ExportService

| Aktuell | Zielbild | Status |
|---|---|---|
| CAMT.053 direkt im HTTP-Response-Stream | `triggerAsyncExport()` → S3 Upload → Presigned URL | **✅ Implementiert** |
| Kein Retry-Mechanismus | S3-Upload mit idempotenter Key-Struktur | **✅ Implementiert** |
| Kein mandantensicherer Ablageort | S3-Key = `{tenantId}/year=.../...` | **✅ Implementiert** |

### 6.3 ComplianceService

| Aktuell | Zielbild | Aufwand |
|---|---|---|
| `screenAndAssert()` blocking in TX-Thread | Pub/Sub via `compliance-screening` Topic | **Hoch** |
| Direkte HTTP-Calls zu Chainalysis | Chainalysis-Consumer als eigener Service | **Hoch** |
| Circuit Breaker in-process | Resilience am Kafka-Consumer-Level | **Mittel** |

### 6.4 CoreBankingClient (Ablösung GENO pago ZS)

| Aktuell | Zielbild | Aufwand |
|---|---|---|
| HTTP-Mock → GENO pago ZS (MQ/COBOL) | REST-API zu neuem Cloud-Native-Kernbanksystem | **Sehr Hoch** |
| `createHold()`, `releaseHold()` synchron | Saga-Pattern via Kafka (Request/Reply) | **Hoch** |
| `createLedgerBooking()` synchron | Event-Sourcing: Ledger-Service konsumiert `stablecoin-transfers` | **Hoch** |

---

## 7. Dev-Profil: Kafka-Simulation ohne Broker

### 7.1 Architektur

```
B2bTransferService
  │ kafkaEventProducer.publishTransferStatus(event)
  ▼
DevKafkaProducer (@Profile("dev"))
  │ 1. JSON serialisieren (ObjectMapper)
  │ 2. Log: [DEV-KAFKA] → stablecoin-transfers: {...}
  │ 3. eventStore.add(topic, eventType, json)
  │ 4. applicationEventPublisher.publishEvent(KafkaSimulationEvent)
  ▼
DevKafkaConsumer (@EventListener, @Async)
  │ [DEV-KAFKA] ← stablecoin-transfers | tx=xyz FUNDS_HELD → SUBMITTED
  │ [DEV-KAFKA] ← compliance-screening | wallet=0xA100...
  │ [DEV-KAFKA] ← stablecoin-analytics-v1 | Lakehouse simuliert
  ▼
InMemoryEventStore
  │ GET /api/v1/dev/events               → letzte 200 Events
  │ GET /api/v1/dev/events?topic=xyz     → nach Topic gefiltert
  │ DELETE /api/v1/dev/events            → Event-Store leeren
```

### 7.2 S3-Simulation im Dev-Profil

```
POST /api/v1/b2b/export/async-trigger?type=camt053
  → DevExportStorageService.upload()
  → Schreibt nach /tmp/stablecoin-exports/{tenantId}/year=2026/month=08/camt053-....xml
  → generatePresignedUrl()
  → "http://localhost:8080/api/v1/dev/exports/download?token=BASE64TOKEN"

GET /api/v1/dev/exports/download?token=BASE64TOKEN
  → Token validieren (Base64 decode, Expiry prüfen)
  → Datei aus /tmp/stablecoin-exports/ lesen und zurückgeben
  → Content-Type: application/xml oder text/csv

GET /api/v1/dev/exports/list
  → Listet alle Dateien in /tmp/stablecoin-exports/
```

---

## 8. Prod-Migrationsplan

### Phase 1 (Q3 2026 — Bereits erledigt: Dev-Simulation)
- ✅ Kafka-Event-POJOs (JSON-Schemata definiert)
- ✅ KafkaEventProducer Interface
- ✅ DevKafkaProducer/Consumer (Spring ApplicationEvent)
- ✅ S3ExportService Interface + DevExportStorageService
- ✅ B2bTransferService: Event-Publishing bei transitionTo()
- ✅ Analytics Data Product API (GET /api/v1/analytics/**)

### Phase 2 (Q3 2026 — Kafka Integration)
- [ ] `spring-kafka` Dependency in pom.xml hinzufügen
- [ ] `KafkaTemplateEventProducer implements KafkaEventProducer` (@Profile("prod"))
- [ ] Kafka Cluster Deployment (3-Broker, Zookeeper oder KRaft)
- [ ] Topics anlegen: `kafka-topics.sh --create ...`
- [ ] Consumer-Groups deployen (Ledger, Compliance, Notification, Analytics)

**Kafka Prod-Konfiguration (application-prod.yml):**
```yaml
spring:
  kafka:
    bootstrap-servers: kafka1:9092,kafka2:9092,kafka3:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer  # Reines JSON!
      acks: all
      retries: 3
      properties:
        enable.idempotence: true
    consumer:
      group-id: stablecoin-platform
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      auto-offset-reset: earliest
```

### Phase 3 (Q4 2026 — CoreBanking-Ablösung)
- [ ] Neues Cloud-Native CoreBanking-System (REST/Kafka) evaluieren
- [ ] Saga-Orchestration für Hold/Ledger-Flow implementieren
- [ ] GENO pago ZS-Schnittstelle abschalten
- [ ] Parallelbetrieb und Migrations-Tests (Dual-Write)

### Phase 4 (Q1 2027 — Vollständige Entkopplung)
- [ ] ComplianceService als eigenständiger Kafka-Consumer-Microservice
- [ ] CircleWalletClient → Kafka-basierte Asynchronität
- [ ] S3 Prod-Deployment (Ceph im Atruvia-RZ oder AWS S3)
- [ ] Lakehouse-Integration (Iceberg/Delta auf Ceph)

---

## 9. Kafka Producer-Konfiguration (Prod-Stub)

```java
// Wird in Phase 2 implementiert (NICHT im aktuellen Code)
@Service
@Profile("prod")
public class KafkaTemplateEventProducer implements KafkaEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publishTransferStatus(TransferStatusEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(KafkaTopics.STABLECOIN_TRANSFERS, event.transactionId(), json)
                         .whenComplete((result, ex) -> {
                             if (ex != null)
                                 log.error("[KAFKA] Send fehlgeschlagen: {}", ex.getMessage());
                         });
        } catch (JsonProcessingException e) {
            log.error("[KAFKA] Serialisierungsfehler: {}", e.getMessage());
        }
    }
    // ... weitere Methoden analog
}
```

---

## 10. S3 Prod-Konfiguration (AWS SDK v2 Stub)

```java
// Wird in Phase 3 implementiert
@Service
@Profile("prod")
public class AwsS3ExportService implements ExportStorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private static final String BUCKET = "stablecoin-exports";

    @Override
    public String upload(String tenantId, String iban, String exportType,
                         byte[] content, String extension) {
        String s3Key = buildKey(tenantId, iban, exportType, extension);
        s3Client.putObject(
            PutObjectRequest.builder().bucket(BUCKET).key(s3Key)
                .metadata(Map.of("tenantId", tenantId, "exportType", exportType))
                .build(),
            RequestBody.fromBytes(content));
        return s3Key;
    }

    @Override
    public String generatePresignedUrl(String s3Key, Duration validFor) {
        return presigner.presignGetObject(r -> r
            .signatureDuration(validFor)
            .getObjectRequest(g -> g.bucket(BUCKET).key(s3Key))
        ).url().toString();
    }
}
```

---

*Dokument: INTEGRATION_TARGET_ARCHITECTURE.md | Version 1.0 | Atruvia AG 2026*
