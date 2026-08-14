# Quality Gate 6 — Hardening & Go-Live Checkliste
## Atruvia AG — Stablecoin-Zahlungsplattform

**Stand:** August 2026

---

| # | Anforderung | Status | Nachweis |
|---|---|---|---|
| 1 | mTLS-Konfiguration (prod) | ✅ Konfiguriert | `application-prod.yml` (Hikari SSL), `.env.example` (`SSL_KEYSTORE_PATH`, `SSL_TRUSTSTORE_PATH`) |
| 2 | JWT-Authentifizierung (stateless) | ✅ Implementiert | `SecurityConfig.java`, `JwtAuthFilter.java` — HS256, STATELESS, dev-mode-Flag |
| 3 | OWASP Dependency-Check in CI | ✅ Vorhanden | `.github/workflows/ci-backend.yml` — `mvn org.owasp:dependency-check-maven:check`, Report als Artifact |
| 4 | SpotBugs-Analyse in CI | ✅ Vorhanden | `.github/workflows/ci-backend.yml` — `mvn spotbugs:check`, Report als Artifact |
| 5 | Lasttest P99 < 5000 ms (Mock) | 🔄 Zu verifizieren | `/tmp/load_test.py` — 1000 Requests, 50 Threads, P99-Messung |
| 6 | Fehlerrate < 1 % im Lasttest | 🔄 Zu verifizieren | `/tmp/load_test.py` — automatische Pass/Fail-Auswertung |
| 7 | Idempotenz-Korrektheit | ✅ Implementiert | `B2bTransferService.initiate()` — `findByIdempotencyKey()` vor Persistierung, `IdempotencyConflictException` (409) |
| 8 | Compliance-Screening (Chainalysis) | ✅ Implementiert | `ComplianceService.screenAndAssert()` — Fail-Closed Circuit-Breaker, AuditLog |
| 9 | AuditLog INSERT-only | ✅ Implementiert | `AuditLogRepository` — kein `deleteBy…`, kein `update`; Append-Only-Semantik |
| 10 | Vier-Augen-Prinzip (Approval Workflow) | ✅ Implementiert | `B2bTransferService` — `AWAITING_APPROVAL` bei Überschreitung `tx_limit_single`; 24h-Fenster |
| 11 | Transaktionslimits (Taurus) | ✅ Konfiguriert | `app.taurus.single-tx-limit: 1000000.00` in `application.yml`; `TaurusLimitExceededException` |
| 12 | Outbox-Pattern (At-Least-Once) | ✅ Implementiert | `OutboxMessage`, `OutboxProcessor` — atomares Schreiben mit Haupttransaktion |
| 13 | OpenTelemetry-Tracing | ✅ Aktiviert | `management.tracing.sampling.probability: 1.0` in `application.yml` |
| 14 | Produktions-HTTP-Clients | ✅ Implementiert | `client/http/HttpTaurusCustodyClient.java`, `HttpChainalysisClient.java` — echte HTTP-Clients für prod |
| 15 | Circuit-Breaker (Resilience4j) | ✅ Konfiguriert | `application-dev.yml` — `circle-wallet`, `taurus-custody`, `core-banking`, `chainalysis` |
| 16 | MiCA-Compliance-Dokumentation | ✅ Erstellt | `MICA_COMPLIANCE.md` — AML/KYC, Limits, Audit-Trail, DSGVO |
| 17 | API-Dokumentation (OpenAPI 3.1) | ✅ Vorhanden | `/swagger-ui.html`, `/api-docs` — SpringDoc konfiguriert |
| 18 | ISO 20022 CAMT.053-Export | ✅ Implementiert | `GET /api/v1/b2b/export/camt053` — inkl. Blockchain-Hash |
| 19 | DATEV-Export | ✅ Implementiert | `GET /api/v1/b2b/export/datev` — DATEV-kompatibles CSV |
| 20 | SHA-256-Hashing Telefonnummern | ✅ Implementiert | `B2cP2pService.hashPhoneNumber()` — Salt + SHA-256, kein Plaintext in DB |
| 21 | .env.example für Railway | ✅ Erstellt | `.env.example` — alle Umgebungsvariablen dokumentiert |
| 22 | railway.toml | ✅ Konfiguriert | `railway.toml` — Healthcheck `/actuator/health`, Build-Timeout 600s, Restart-Policy |
| 23 | Zero-Downtime-Readiness | ✅ Actuator Health | `/actuator/health` — Railway wartet auf UP vor Traffic-Umschaltung |
| 24 | Bulk-Payment-Import (CSV) | ✅ Implementiert | `POST /api/v1/b2b/bulk-payments` — Multipart-CSV, pro Row eigene TX |
| 25 | Adressbuch (Whitelist) | ✅ Implementiert | `AddressBookService` — `POST/GET/DELETE /api/v1/b2b/address-book` |

---

## Offene Punkte vor Go-Live

| # | Aufgabe | Priorität |
|---|---|---|
| A | Lasttest `/tmp/load_test.py` gegen laufenden Dev-Stack ausführen und Ergebnis dokumentieren | Hoch |
| B | Railway-Umgebungsvariablen in Dashboard hinterlegen (alle aus `.env.example`) | Hoch |
| C | DB-Rolle in Prod auf INSERT/SELECT für `audit_log` beschränken (kein DELETE/UPDATE) | Hoch |
| D | mTLS-Zertifikate generieren und in `SSL_KEYSTORE_PATH` / `SSL_TRUSTSTORE_PATH` bereitstellen | Mittel |
| E | Separaten `approverId ≠ initiatorId`-Check im Approval-Flow via IAM absichern | Mittel |
| F | OWASP-Findings aus CI-Artifact prüfen und kritische CVEs adressieren | Hoch |

---

*Erstellt im Rahmen von Phase 6: Lasttests, Dokumentation & Deployment.*
