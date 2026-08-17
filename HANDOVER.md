# Handover-Dokument — Atruvia Stablecoin Integration Platform

> Letzte Aktualisierung: 2026-08-15 | GitHub: https://github.com/potty32/stablecoin-integration

---

## 11. Prompt für neue Claude-Instanz

Kopiere diesen Block als erste Nachricht in die neue Session — Claude erledigt den Rest automatisch:

```
Ich arbeite an der Atruvia AG Stablecoin Integration Platform.

1. Klone das Repository: https://github.com/potty32/stablecoin-integration
2. Lies HANDOVER.md vollständig.
3. Führe Abschnitt 7 (Setup) Schritt für Schritt aus:
   - System-Pakete installieren (apt)
   - PostgreSQL einrichten und starten
   - Backend bauen und starten
   - Testdaten einspielen (testdata/seed_dev.sql)
   - Frontend starten
4. Bestätige mit einem Statusbericht: Health-Endpoint, DB-Tabellen, Frontend-URL.

Aktueller Stand: Alle 6 Phasen vollständig implementiert, alle Quality Gates bestanden.
```

---

## 1. Projektkontext

### Auftraggeber & Rolle
- **Atruvia AG** — Digitalisierungspartner der genossenschaftlichen Volksbanken Raiffeisenbanken FinanzGruppe
- Regulatorischer Rahmen: **MiCA** (Markets in Crypto-Assets Regulation, EU 2023/1114)

### Fachliche Vision
- **"Turbo Rail"**: Blockchain-basierte Express-Schiene (USDC/EURC via Circle) parallel zu SWIFT
- Zielgruppen: **B2B** (Firmenkunden) und **B2C** (Privatkunden)
- B2C: Blockchain-Begriffe vollständig abstrahiert (kein "Wallet", "USDC" in der UI)

### Architektur-Standards
- Strikte Schichten: Controller → Service → Repository → Entity
- DTOs (Java Records) — Entities nie direkt ans Frontend
- Profile: `dev` (lokale PostgreSQL) | `prod` (Env-Vars, mTLS)

---

## 2. Technologie-Stack

| Schicht | Technologie |
|---|---|
| Backend | Spring Boot 3.3.5, Java 21, Maven |
| Datenbank | PostgreSQL 16 + Flyway (V1 init, V2 B2B-Limit-Fix) |
| Frontend | Angular 18, TypeScript, Standalone Components |
| Auth | JWT HS256, Secret in `application-dev.yml` |
| Externe APIs (Mock) | Circle (USDC/EURC), Taurus (MPC), Chainalysis (AML), n8n |
| Resilienz | Resilience4j Circuit Breaker |
| Observability | OpenTelemetry Tracing, AuditLog (INSERT-only) |

---

## 3. Repository-Struktur

```
stablecoin-integration/
├── backend/
│   └── src/main/java/de/atruvia/stablecoin/
│       ├── controller/b2b/          # B2B REST-Endpunkte
│       ├── controller/b2c/          # B2C REST-Endpunkte
│       ├── controller/common/       # /accounts/{iban}/balance
│       ├── service/b2b/             # Transfer, AddressBook, BulkPayment, Export
│       ├── service/b2c/             # Remittance, P2P, Yield, Micropayment
│       ├── client/mock/             # @Profile("dev") — alle 5 Clients gemockt
│       ├── client/http/             # @Profile("prod") — RestClient
│       ├── exception/               # GlobalExceptionHandler
│       └── resources/
│           ├── application-dev.yml  # Lokale PostgreSQL, JWT-Secret, Resilience4j
│           └── db/migration/        # V1__init.sql, V2__fix_b2b_approval_threshold.sql
├── frontend/
│   └── src/app/
│       ├── core/services/
│       │   ├── auth.service.ts      # JWT dekodieren, IBAN pro Profil
│       │   └── transaction.service.ts
│       ├── features/b2b/            # Transfers, Approvals, AddressBook
│       ├── features/b2c/            # Remittance, P2P, Yield
│       └── features/login/          # Login-Seite (kein Dev-Tools nötig)
├── testdata/
│   └── seed_dev.sql                 # Manueller DB-Dump (Testdaten dieser Session)
├── HANDOVER.md
├── MICA_COMPLIANCE.md
└── QG6_CHECKLIST.md
```

---

## 4. Fachliche Schlüsselregeln

| Regel | Wert |
|---|---|
| Approval-Threshold (Vier-Augen) | > 25.000 EUR |
| Taurus Single-TX-Limit | 1.000.000 EUR → HTTP 403 (`TAURUS_001`) |
| Circle Mock-Delay (Settlement) | 3 Sekunden → COMPLETE |
| FX Rate Quote Gültigkeit | 60 Sekunden |
| Phone-Alias Hashing | SHA-256 + Salt `"atruvia-stablecoin-2026"` |
| Yield Rate | 3,5% p.a. (Zinseszins täglich: `principal × (1 + 0.035/365)^days`) |
| Micropayment Max | 10 EUR |
| High-Risk Test-Adresse | `0xDEAD000000000000000000000000000000000000` → HTTP 403 |
| AuditLog | INSERT-only, niemals UPDATE/DELETE |
| B2B Gebühr | 2,50 EUR + 0,15% FX-Spread |
| B2C Remittance-Gebühr | 0,50 EUR |
| P2P-Gebühr | 0,00 EUR |

---

## 5. Bekannte Besonderheiten & Fixes (wichtig!)

### Circuit Breaker — `ComplianceBlockException` ignorieren
`ComplianceBlockException` ist ein fachliches Ergebnis (keine Systemstörung). Ohne `ignore-exceptions` öffnet der Circuit Breaker nach der ersten blockierten Adresse und blockt alle Folgeaufrufe mit "UNAVAILABLE".
**Fix ist in `application-dev.yml` bereits konfiguriert:**
```yaml
resilience4j.circuitbreaker.instances.chainalysis.ignore-exceptions:
  - de.atruvia.stablecoin.exception.ComplianceBlockException
```

### Yield Deposit — Body-Pflicht
`POST /api/v1/b2c/savings/yield/deposit` erwartet `{ sourceIban, amountEur }` im Body.
Der Frontend-Service `depositYield()` übergibt beide Felder korrekt seit Fix dieser Session.

### Yield Redeem — Status-Semantik
Eingelöste Positionen erhalten Status `FAILED` (interner Marker, nicht ein echter Fehler).
Die `getPosition()`-Abfrage sucht nur `SETTLED`-Einträge, daher verschwindet die Position nach dem Einlösen korrekt.

### Phone-Alias Hashing
Der Backend-Hash lautet: `SHA-256("atruvia-stablecoin-2026" + phoneNumber)`.
Beim direkten DB-Insert immer mit diesem Salt hashen, sonst schlägt die P2P-Lookup fehl.

---

## 6. Seed-Accounts (Flyway V1)

| Account | customerId | IBAN | Typ | KYC | TX-Limit |
|---|---|---|---|---|---|
| B2B | cust-b2b-001 | DE89370400440532013000 | B2B | TIER_3 | 25.000 EUR |
| B2C | cust-b2c-001 | DE27200400600532013001 | B2C | TIER_2 | 5.000 EUR |

### JWT erzeugen (Python — Secret aus `application-dev.yml`)
```python
import hmac, hashlib, base64, json, time

secret = "<JWT_SECRET aus application-dev.yml>"
now = int(time.time())
h = base64.urlsafe_b64encode(json.dumps({"alg":"HS256","typ":"JWT"}).encode()).rstrip(b"=").decode()
p = base64.urlsafe_b64encode(json.dumps({"sub":"cust-b2b-001","iat":now,"exp":now+86400}).encode()).rstrip(b"=").decode()
sig = base64.urlsafe_b64encode(hmac.new(secret.encode(), f"{h}.{p}".encode(), hashlib.sha256).digest()).rstrip(b"=").decode()
print(f"{h}.{p}.{sig}")
```

---

## 7. Setup auf neuem Rechner (Ubuntu 24.04, apt — kein Docker nötig)

```bash
# 1. System-Pakete (einmalig)
sudo apt-get update -qq
sudo apt-get install -y openjdk-21-jdk maven postgresql postgresql-client nodejs npm

# 2. Repository klonen
git clone https://github.com/potty32/stablecoin-integration.git
cd stablecoin-integration

# 3. PostgreSQL einrichten
sudo service postgresql start
sudo -u postgres psql -c "CREATE USER stablecoin WITH PASSWORD 'stablecoin_dev_pass';"
sudo -u postgres psql -c "CREATE DATABASE stablecoin_dev OWNER stablecoin;"
sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE stablecoin_dev TO stablecoin;"

# 4. Backend bauen und starten (Flyway läuft automatisch)
cd backend
mvn package -DskipTests -q
SPRING_PROFILES_ACTIVE=dev nohup java -jar target/stablecoin-backend-1.0.0.jar \
  --server.port=8080 > /tmp/backend.log 2>&1 &
sleep 20 && curl -s http://localhost:8080/actuator/health
# → {"status":"UP"}

# 5. Testdaten einspielen (NACH Backend-Start, damit Flyway-Schema existiert)
cd ..
sudo -u postgres psql -d stablecoin_dev -f testdata/seed_dev.sql

# 6. Frontend starten
cd frontend
npm install
npx ng serve --proxy-config proxy.conf.json --host 0.0.0.0 --port 4200 \
  > /tmp/frontend.log 2>&1 &
sleep 15 && tail -3 /tmp/frontend.log
# → "Application bundle generation complete."

# 7. App öffnen: http://localhost:4200
#    → Login-Seite, Profil wählen (B2B oder B2C), fertig
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
| POST | /transfers | Transfer initiieren (`X-Idempotency-Key` Pflicht) |
| GET | /transfers | Liste (paginiert, `?status=&page=&size=`) |
| POST | /transfers/{id}/approve | Vier-Augen-Freigabe |
| POST | /transfers/{id}/reject | Ablehnung |
| GET | /rate-quote | FX-Kurs sichern (`?amountEur=&currency=`) |
| POST | /address-book | Adresse whitelisten (Chainalysis-Screen) |
| GET | /address-book | Whitelist auflisten |
| DELETE | /address-book/{id} | Adresse widerrufen |
| POST | /bulk-payments | CSV-Upload (multipart/form-data) |
| GET | /export/camt053 | ISO-20022 CAMT.053 |
| GET | /export/datev | DATEV-CSV |

### B2C (`/api/v1/b2c/`)
| Methode | Pfad | Zweck |
|---|---|---|
| POST | /remittances | Auslandsüberweisung (0,50 EUR Fee) |
| POST | /p2p/phone/register | Telefon-Alias registrieren |
| POST | /p2p/phone | P2P via Telefonnummer |
| POST | /savings/yield/deposit | Sparkonto eröffnen (`{sourceIban, amountEur}`) |
| GET | /savings/yield | Aktuelle Yield-Position |
| DELETE | /savings/yield/{id} | Position auflösen |
| POST | /micropayments | Biometrie-Kleinstzahlung (max. 10 EUR) |

### Gemeinsam
| Methode | Pfad |
|---|---|
| GET | /api/v1/accounts/{iban}/balance |
| GET | /api/v1/transactions/{id} |
| GET | /actuator/health |

---

## 9. Frontend — Login & Auth

Die App hat eine Login-Seite (`/login`) ohne Passwort:
- **🏢 Firmenkunde** → setzt JWT für `cust-b2b-001`, leitet zu `/b2b/transfers`
- **👤 Privatkunde** → setzt JWT für `cust-b2c-001`, leitet zu `/b2c/remittances`
- **Abmelden**-Button oben rechts → zurück zur Login-Seite

Auth-Guard schützt alle `/b2b/*` und `/b2c/*` Routen.
`AuthService` (`core/services/auth.service.ts`) dekodiert den JWT und liefert die IBAN — wird in allen Formularen für die Vorausfüllung verwendet.

---

## 10. Testdaten dieser Session

### B2B Adressbuch (5 Einträge)
| Label | Wallet | Währung |
|---|---|---|
| Müller GmbH | 0xA100...0001 | USDC |
| Schmidt AG | 0xA200...0002 | EURC |
| Tech Ventures | 0xA300...0003 | USDC |
| Logistics Partner | 0xA400...0004 | EURC |
| Clearinghaus EU | 0xA500...0005 | USDC |

### B2B Transfers
- 3× SETTLED (1.500 / 8.750 / 23.999 EUR)
- 3× AWAITING_APPROVAL (75.000 / 148.500 / 50.000 EUR) — bereit zum Freigeben testen
- 2× FAILED (Compliance-Block, Taurus-Limit)
- 2× historisch SETTLED (9.800 / 55.000 EUR, 14–30 Tage alt)

### B2C
- 5 registrierte Telefonnummern (Phone-Aliases)
- 6× Remittance SETTLED + 1× FAILED
- 2× P2P SETTLED
- 1× Yield-Deposit SETTLED (2.000 EUR, 7 Tage alt → ~1,34 EUR Ertrag)

### Registrierte Telefonnummern (für P2P-Test)
| Nummer | Name |
|---|---|
| +4915112345678 | Anna Müller |
| +4917698765432 | Ben Schmidt |
| +521551234567 | Carlos Ramirez |
| +639171234567 | Maria Santos |
| +2348012345678 | Chidi Okafor |

---

## 11. Offene Punkte / Nächste Schritte

> Stand: 2026-08-17 | ✅ = erledigt | 🔴 = offen/kritisch | 🟡 = offen/mittel | 🔵 = offen/niedrig

### ✅ Erledigte Punkte (diese Session)
- ~~Kontostand-Anzeige im Frontend~~ → Balance-Widget in `TransferListComponent`, Ownership-Check im Controller
- ~~Rate-Quote Live-FX-Kurs für USDC~~ → `FxRateService` + `HttpEcbRateClient` (ECB-Referenzkurs)
- ~~Adressbuch Whitelist-Erzwingung~~ → Transfer schlägt mit 403 `NOT_WHITELISTED` fehl
- ~~Adressbuch Sanctions-Batch~~ → `SanctionsBatchService` (cron 02:00, REVOKED + n8n)
- ~~Adressbuch institutionelle Whitelist~~ → `InstitutionalAddressBook` Entity + 3 REST-Endpunkte
- ~~IAM für Approver~~ → `approverId` aus JWT, Selbst-Genehmigung blockiert
- ~~Integrationstests~~ → 99 Tests (Unit + Integration), Coverage 62,7% LINE

---

### Testabdeckung (Ziel: ≥ 80% LINE für Produktions-Abnahme)

**Aktueller Stand: 62,7% LINE / 48,4% BRANCH (99 Tests)**

| Priorität | Maßnahme | Erwarteter Gewinn |
|---|---|---|
| 🔴 **Hoch** | **`controller/b2b` via MockMvc** — alle 14 REST-Endpunkte: HTTP-Status, Request-Validierung, `X-Idempotency-Key`-Pflicht | +15–20pp LINE |
| 🔴 **Hoch** | **`service/b2c` Unit-Tests** — P2P, Remittance, Micropayment analog zu `B2cYieldServiceTest` (aktuell 40%) | +10–15pp LINE |
| 🟡 **Mittel** | **`client/http` Contract-Tests** — WireMock gegen Circle/Taurus/ECB-API-Spezifikation (kein echter API-Key nötig, `@Profile("prod")`) | +8pp LINE |

Ziel: **≥ 80% LINE, ≥ 70% BRANCH** — für BaFin/IT-Audit erforderlich.

---

### Security Test (Penetration-Test)

🔴 **Kritisch vor Produktivbetrieb** — folgende Bereiche müssen geprüft werden:

| Testbereich | Beschreibung | Tool-Empfehlung |
|---|---|---|
| **JWT-Sicherheit** | Algorithmus-Downgrade (alg=none), schwache Secrets, Token-Replay | jwt_tool, Burp Suite |
| **Authentifizierung** | Bypasses in Dev-Mode (`app.security.dev-mode`), Brute-Force auf Login | OWASP ZAP |
| **Authorization** | IDOR: Ownership-Check auf alle `/api/v1/accounts/{iban}` und `/api/v1/transactions/{id}` Endpunkte, Horizontal Privilege Escalation | Burp Suite Pro |
| **Input Validation** | SQL-Injection in Query-Parametern (z.B. `?status=`), XSS in String-Feldern, Path-Traversal | OWASP ZAP, SQLMap |
| **API Rate Limiting** | Kein Rate-Limiting implementiert — Brute-Force und DoS möglich | k6, Artillery |
| **Dependency-CVEs** | Bekannte Schwachstellen in Spring Boot 3.3.5, Jackson, JJWT | `mvn dependency-check:check` (braucht `NVD_API_KEY` als GitHub Secret) |
| **mTLS / TLS** | Zertifikats-Validierung in Prod (`SSL_KEYSTORE_PATH`), Cipher-Suite-Prüfung | testssl.sh, sslyze |
| **Blockchain** | On-Chain-Transaktionsverifizierung, Wallet-Adress-Validierung | Manuelle Review |

**Empfohlene Vorgehensweise:**
1. OWASP ZAP Automated Scan gegen `http://localhost:8080` (dev-Umgebung)
2. Burp Suite Pro für manuelle IDOR- und Auth-Tests mit zwei verschiedenen JWT-Tokens
3. `mvn org.owasp:dependency-check-maven:check` (benötigt `NVD_API_KEY` als GitHub Secret: Settings → Secrets → Actions)
4. Vor Railway-Deploy: `testssl.sh` gegen die Prod-URL

---

### Deployment (Prod)

- 🟡 **Angular Production Build**: `ng build --configuration production` noch nicht verifiziert
- 🔵 **Railway Deploy**: Prod-Secrets (Circle/Taurus/Chainalysis API-Keys) eintragen
- 🔵 **mTLS-Zertifikate**: Für Railway manuell bereitstellen (`SSL_KEYSTORE_PATH`)
- 🟡 **OWASP CVE-Scan CI**: `NVD_API_KEY` als GitHub Secret hinterlegen (Settings → Secrets → Actions)
