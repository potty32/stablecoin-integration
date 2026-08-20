package de.atruvia.stablecoin.service.common;

import de.atruvia.stablecoin.dto.response.DevChatResponse;
import de.atruvia.stablecoin.entity.TenantSettings;
import de.atruvia.stablecoin.service.b2b.TenantSettingsService;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Semantische Wissensdatenbank für den Atruvia Stablecoin Copilot.
 *
 * Implementiert einen Keyword- und Pattern-Matcher über strukturierte KnowledgeEntries.
 * Jeder Eintrag hat Trigger-Begriffe, eine Antwort-Vorlage und Quellenangaben.
 * Antworten können mandantenspezifisch personalisiert werden.
 */
@Service
public class DevChatKnowledgeService {

    private final TenantSettingsService tenantSettingsService;

    public DevChatKnowledgeService(TenantSettingsService tenantSettingsService) {
        this.tenantSettingsService = tenantSettingsService;
    }

    // ── Knowledge-Entry-Struktur ─────────────────────────────────────────────────

    private record KnowledgeEntry(
            List<String> triggers,
            String reply,
            List<String> sources) {

        boolean matches(String query) {
            String q = query.toLowerCase(Locale.GERMAN);
            return triggers.stream().anyMatch(t -> q.contains(t.toLowerCase(Locale.GERMAN)));
        }
    }

    // ── Statische Wissensdatenbank ───────────────────────────────────────────────

    private static final List<KnowledgeEntry> KNOWLEDGE_BASE = List.of(

        // ── A1: RLS / Multi-Tenancy ─────────────────────────────────────────────
        new KnowledgeEntry(
            List.of("rls", "row level security", "isolat", "daten trenn", "multi-tenancy",
                    "mandant", "tenant", "datentrennung", "volksbank"),
            """
            **Mandantentrennung via PostgreSQL Row-Level Security (RLS)**

            Die Plattform isoliert Daten zwischen den ~800 VR-Banken (Mandanten) durch \
            PostgreSQL RLS auf den folgenden Tabellen:

            **Geschützte Tabellen (V8-Migration):**
            - `stablecoin_transaction`, `customer_account`, `address_book`
            - `yield_position`, `audit_log`, `dvp_escrow` (V20)
            - `phone_alias` (V23)

            **Zwei-Rollen-Modell:**
            - **`stablecoin`** (Table-Owner, Flyway-User): Bypasses RLS automatisch — wird nur für \
              Migrationen und Cross-Tenant-Lookups (`adminJdbcTemplate`) genutzt
            - **`stablecoin_app`** (App-User): Unterliegt vollständig den RLS-Policies

            **PostgreSQL Policy:**
            ```sql
            CREATE POLICY tenant_isolation_policy ON stablecoin_transaction
                USING (tenant_id = current_setting('app.current_tenant', true));
            ```

            **Ablauf pro Request:**
            1. `JwtAuthFilter` liest `tenant`-Claim aus JWT → `TenantContext.set(tenantId)`
            2. `TenantAwareDataSource.getConnection()` → `set_config('app.current_tenant', ?, false)`
            3. PostgreSQL filtert automatisch — kein Code-seitiger WHERE-Clause nötig
            4. `TenantEntityListener @PrePersist` setzt `tenant_id` beim Persistieren

            **Ergebnis:** Kein einziger DB-Call von Mandant A kann Daten von Mandant B sehen.
            """,
            List.of("[PostgreSQL RLS Doku]", "[V8__enable_row_level_security.sql]",
                    "[TenantAwareDataSource.java]", "[TenantEntityListener.java]")
        ),

        // ── A2: Outbox Pattern / Crash Recovery ─────────────────────────────────
        new KnowledgeEntry(
            List.of("outbox", "crash", "absturz", "recovery", "ausfall", "at-least-once",
                    "neustart", "outbox-processor", "blockchain bestätigung"),
            """
            **Transactional Outbox Pattern — Crash-sichere Verarbeitung**

            Das Outbox-Pattern garantiert Konsistenz zwischen Datenbank-State und \
            externen API-Calls (Circle, Taurus), selbst bei Systemabstürzen.

            **Ablauf:**
            1. Transaktion wird in DB als `FUNDS_HELD`/`SUBMITTED` persistiert
            2. **Gleichzeitig** (gleiche Transaction) wird ein `OutboxMessage`-Eintrag \
               (`SUBMIT_TO_BLOCKCHAIN`) in `outbox_message` gespeichert
            3. Nach Commit: Circle-API wird aufgerufen (außerhalb der Transaktion)

            **Crash-Recovery:**
            - `OutboxProcessor` prüft alle **5 Sekunden** `PENDING`-Einträge
            - `SUBMIT_TO_BLOCKCHAIN`: Pollt Circle-API für `circleTransactionId` → bei \
              `COMPLETE` → `settleTransaction()`, bei `FAILED` → `transitionToFailed()`
            - `PROCESS_INBOUND_COMPLIANCE`: Startet AML-Screening neu (Idempotenz via \
              `blockchainHash`-Check)
            - **SKIP LOCKED** (V23-Update): Clustersichere Verarbeitung — kein \
              Doppel-Processing in Multi-Instanz-Deployment

            **At-Least-Once Delivery:** Eine Nachricht wird mindestens einmal verarbeitet. \
            Idempotenz-Keys verhindern Doppelbuchungen im Core-Banking.
            """,
            List.of("[OutboxProcessor.java]", "[outbox_message Tabelle]",
                    "[V1__init.sql — outbox_message Schema]")
        ),

        // ── A3: Idempotenz ──────────────────────────────────────────────────────
        new KnowledgeEntry(
            List.of("idempotenz", "race condition", "doppelbuchung", "doppel",
                    "zweimal", "idempotency", "replay", "psd2"),
            """
            **Idempotenz-Schutz (PSD2-konform)**

            **Technische Umsetzung:**
            - Jede Transaktion erhält einen eindeutigen `idempotency_key` \
              (z. B. `X-Idempotency-Key`-Header des API-Aufrufs)
            - DB-UNIQUE-Constraint auf `stablecoin_transaction.idempotency_key` verhindert \
              physische Duplikate
            - `persistInitialTransaction()` prüft **atomisch** (in einer `@Transactional`): \
              existiert der Key bereits → `IdempotencyConflictException` (HTTP 409)

            **TTL (G-09):** Idempotenz-Keys verfallen nach **30 Tagen** \
            (`idempotency_expires_at`). Ein nächtlicher Job (`IdempotencyCleanupService`) \
            bereinigt veraltete Einträge.

            **Inbound-Webhooks:** Natürlicher Idempotenz-Key = `blockchainHash` der \
            On-Chain-Transaktion. Doppelte Circle-Webhooks werden sicher ignoriert.

            **PSD2-Pflicht:** Artikel 94(2) PSD2 schreibt vor, dass Zahlungsauslöser \
            mindestens 30 Tage lang Idempotenz garantieren müssen.
            """,
            List.of("[PSD2 Art. 94(2)]", "[StablecoinTransactionRepository.java]",
                    "[B2bTransferService.java — persistInitialTransaction]")
        ),

        // ── B1: BaFin / MaRisk / BAIT ───────────────────────────────────────────
        new KnowledgeEntry(
            List.of("bafin", "bait", "marisk", "aufsicht", "auslagerung", "wesentlich",
                    "dora", "notfallkonzept", "§25a", "kreditinstitut"),
            """
            **BaFin-Compliance — MaRisk / BAIT**

            **MaRisk AT 4.3.4 — Wesentliche Auslagerungen:**
            Die Nutzung von Circle (Stablecoin-Issuance/Settlement), Taurus (MPC-Custody) \
            und Chainalysis (AML-Screening) wird als **wesentliche Auslagerung** nach \
            MaRisk AT 4.3.4 deklariert. Anforderungen:

            - **Auslagerungsregister** mit SLA-Dokumentation
            - **Exit-Strategie** (Adapter-Abstraktion ermöglicht Provider-Wechsel)
            - **Notfallkonzept:** Bei Chainalysis-Ausfall → **Fail-Closed** (alle Transaktionen \
              werden blockiert, nicht durchgelassen) + n8n-Compliance-Alert

            **BAIT (Bankaufsichtliche Anforderungen an die IT):**
            - Lückenlose `AuditLog`-Tabelle (INSERT-only, tamper-proof)
            - Kill-Switch (`/admin/kill-switch`) für sofortigen Zahlungsstopp (DORA Art. 17)
            - Alle Konfigurationsänderungen via `LimitChangeLog` revisionssicher

            **§25a KWG — IT-Sicherheit:**
            - RBAC: Admin-Endpunkte nur mit `ROLE_ATRUVIA_ADMIN` (JWT-Claim)
            - Phone-Nummern HMAC-SHA256-gehashed (kein Klartext-PII in DB)
            """,
            List.of("[BaFin MaRisk AT 4.3.4]", "[BAIT Kapitel 4]",
                    "[DORA Art. 17]", "[§25a KWG]")
        ),

        // ── B2: MiCA / Travel Rule / TFR ────────────────────────────────────────
        new KnowledgeEntry(
            List.of("mica", "e-money", "emt", "tfr", "travel rule", "geldwäscheverordnung",
                    "fatf", "originator", "beneficiary", "eu-verordnung", "usdc", "eurc",
                    "stablecoin regulier"),
            """
            **MiCA & EU Transfer of Funds Regulation (TFR)**

            **MiCA-Konformität:**
            - USDC und EURC sind E-Money-Token (EMT) gemäß MiCA-Klassifizierung
            - AllUnity EURAU: BaFin-reguliert, MiCA Art. 36 Deckungsprüfung \
              (Reserve ≥ 105% vor jedem Transfer)
            - Qivalis EURQ: DZ-Bank-Konsortium, 2-of-N Multisig-Verfahren

            **EU TFR / FATF Recommendation 16 (Travel Rule):**
            Die reformierte EU-Geldwäscheverordnung (Reg. 2023/1113) verlangt:
            - **Ab €0** für Self-Hosted Wallets: Originator- und Beneficiary-Stammdaten \
              (`beneficiary_name`, `beneficiary_address`, `beneficiary_account_id`)
            - Diese Daten werden im `InitiateTransferRequest` erfasst und in \
              `StablecoinTransaction` persistiert (V17-Migration)
            - `travelRuleEnabled` steuert die Schwelle; `travelRuleCompletedAt` markiert \
              die VASP-zu-VASP-Übertragung

            **Status (Backlog Phase 3):**
            - VASP-Transmission (OpenVASP/TRISA-Protokoll) noch nicht implementiert
            - Threshold aktuell 15.000 EUR (korrekt: €0 für Self-Hosted) → Backlog C-01
            """,
            List.of("[MiCA Reg. 2023/1114]", "[EU TFR Reg. 2023/1113]",
                    "[FATF Rec. 16]", "[V17__travel_rule.sql]")
        ),

        // ── B3: Sanctions / AML / GwG ───────────────────────────────────────────
        new KnowledgeEntry(
            List.of("sanktion", "geldwäsche", "gwg", "sperrung", "0xdead", "aml",
                    "chainalysis", "compliance", "verdacht", "fiu", "screening"),
            """
            **Sanctions & AML-Compliance (GwG §43)**

            **Technischer Ablauf bei Sanktionstreffer:**
            1. `ComplianceService.screenAndAssert()` sendet Wallet-Adresse an \
               **Chainalysis KYT API** (direction=`incoming`/`outgoing`)
            2. Bei HIGH_RISK / SANCTIONS-Treffer → `ComplianceBlockException`
            3. Inbound: TX wird `COMPLIANCE_REJECTED` → `FAILED` — **keine Gutschrift** auf \
               Kundenkonto, Funds bleiben auf Custody-Wallet blockiert
            4. Outbound: Hold wird freigegeben, Transaktion abgebrochen

            **Test-Adresse:** `0xDEAD000000000000000000000000000000000000` \
            → wird im Mock immer als HIGH_RISK klassifiziert

            **GwG §43 Verdachtsmeldung:**
            - `AML_INBOUND_BLOCK`-Eintrag in `audit_log` (BaFin-revisionssicher)
            - n8n-Webhook-Trigger → Compliance-Dashboard-Alert

            ⚠️ **Backlog F-04:** Vollautomatischer FIU-Online-Meldeweg (Strafbewehrung \
            §56 Abs. 1 Nr. 70 GwG: bis 5 Mio. EUR Bußgeld) noch in Entwicklung.

            **Fail-Closed:** Bei Chainalysis-Ausfall (Circuit Breaker offen) → alle \
            Transaktionen blockiert, Quarantäne-Status, kein Fail-Open.
            """,
            List.of("[GwG §43 Verdachtsmeldung]", "[Chainalysis KYT API]",
                    "[ComplianceService.java]", "[BAIT Kapitel 4.3]")
        ),

        // ── C1: Yield / Zinsen ──────────────────────────────────────────────────
        new KnowledgeEntry(
            List.of("zinsen", "yield", "zinssatz", "sparkonto", "sparen", "3,5", "3.5",
                    "rendite", "jahreszins", "zinseszins", "datev", "kapitalertrag",
                    "jahressteuer", "steuer"),
            """
            **B2C Yield-Sparkonto — Zinsen & Steuer**

            **Produktstruktur:**
            - **Zinssatz:** 3,5% p.a. (konfigurierbar per Tenant, aktuell hardcoded — Backlog F-09)
            - **Zinsberechnung:** Tägliche Akkumulation (`YIELD_DEPOSIT` → `YieldPosition`)
            - **Einzahlung:** Typ `YIELD_DEPOSIT`, Status immer `SETTLED` (unveränderlicher \
              Buchungsbeleg, BaFin-konform)
            - **Auflösung:** `YIELD_REDEEM` erzeugt separate Auszahlungs-TX mit Nettobetrag \
              nach Quellensteuer

            **Lebenszyklus YieldPosition:**
            `ACTIVE` → (Zinsakkumulation täglich) → `CLOSED` (nach Auflösung)

            **Steuerliche Erfassung (WpHG / EStG §43/§44):**
            - `TaxEvent`-Entität (V13) speichert: `grossYieldEur`, `taxWithheldEur`, \
              `netPayoutEur`, `taxReferenceId` (Atruvia Tax Engine)
            - Freistellungsauftrag (FSA): 1.000 EUR Freibetrag pro Jahr (§20 EStG)
            - `ExportService` erzeugt DATEV-CSV mit Zins- und Kapitaltrennung

            **Jahresabschluss (G-15, V18):**
            `YieldYearEndService` bewertet alle ACTIVE-Positionen zum 31.12. steuerlich \
            (EStG §11 Realisationsprinzip, HGB §252). Duplikat-Schutz via \
            `last_valued_year`-Feld.

            ⚠️ **Backlog C-03:** FSA-Abzug muss kumulativ pro Jahr statt pro Einlösung \
            berechnet werden.
            """,
            List.of("[EStG §43/§44 Kapitalertragsteuer]", "[B2cYieldService.java]",
                    "[TaxEvent.java — V13]", "[YieldYearEndService.java — G-15]")
        ),

        // ── A4: DvP / Tokenisierte Wertpapiere ─────────────────────────────────
        new KnowledgeEntry(
            List.of("dvp", "delivery versus payment", "wertpapier", "tokenisiert",
                    "herstatt", "escrow", "deka", "union investment", "clearstream"),
            """
            **Delivery-versus-Payment (DvP) — Atomare Wertpapierabwicklung**

            Das DvP-Escrow-Modul eliminiert das Herstatt-Risiko bei tokenisierten \
            Wertpapiergeschäften:

            **Ablauf (3 Endpunkte):**
            1. **`POST /dvp/lock`** — Stablecoin-Betrag wird gesperrt (`ESCROWED`), \
               EUR-Hold auf Kundenkonto via CoreBanking
            2. **`POST /dvp/settle`** — Wertpapierabwicklung bestätigt Delivery → \
               Stablecoins an Händler-Wallet, Gebühren gebucht (`SETTLED`)
            3. **`POST /dvp/cancel`** — Delivery gescheitert → Hold freigegeben, \
               Betrag zurück (`CANCELLED`)

            **Datenbankmodell:** `dvp_escrow`-Tabelle (V20) mit RLS (Mandantentrennung)
            **Unterstützte Systeme:** Deka Investment, Union Investment, Clearstream
            **Währungen:** USDC, EURC, EURAU, EURQ (via Token-Adapter-Pattern)
            """,
            List.of("[UC-33/34/35 — DvP Escrow Engine]",
                    "[V20__dvp_escrow_and_multi_token.sql]",
                    "[DvpEscrowService.java]")
        ),

        // ── A5: Token-Adapter / Multi-Issuer ────────────────────────────────────
        new KnowledgeEntry(
            List.of("token adapter", "allunity", "qivalis", "eurau", "eurq", "circle",
                    "multi-issuer", "multi-token", "stablecoin issuer"),
            """
            **Multi-Token-Adapter-Pattern**

            Die Plattform unterstützt seit Sprint 2026-08-20 mehrere Stablecoin-Issuers:

            | Token | Issuer | Adapter |
            |-------|--------|---------|
            | USDC / EURC | Circle (Circle W3S API) | `CircleTokenAdapter` |
            | EURAU | AllUnity (BaFin-reguliert) | `AllUnityTokenAdapter` |
            | EURQ | Qivalis / DZ Bank Konsortium | `QivalisTokenAdapter` |

            **Erweiterung:** Neue Issuers = neue `@Component StablecoinTokenAdapter`-Bean → \
            `TokenAdapterRouter` registriert automatisch beim Start.

            **BaFin-Compliance (AllUnity):** `assertBaFinCoverage()` prüft MiCA Art. 36 \
            Deckungsquote ≥ 105% vor jedem EURAU-Transfer.

            ⚠️ **Backlog C-02:** `HttpAllUnityClient` für prod-Profil implementieren.
            """,
            List.of("[StablecoinTokenAdapter.java]", "[TokenAdapterRouter.java]",
                    "[MiCA Art. 36 Deckungspflicht]")
        ),

        // ── Limits & Gebühren ───────────────────────────────────────────────────
        new KnowledgeEntry(
            List.of("limit", "gebühr", "spread", "fee", "tageslimit", "transaktionslimit",
                    "vier-augen", "approval", "freigabe"),
            """
            **Transaktionslimits & Gebühren (mandantenspezifisch)**

            **Limit-Hierarchie (`LimitResolver`):**
            - `customer_account.tx_limit_single` (Kunden-Override, max. B2B: 200k EUR)
            - `tenant_settings.tx_limit_single_b2b` (Bank-Obergrenze, 500k EUR)
            - `tenant_settings.approval_threshold_b2b` (Vier-Augen-Schwelle, 25k EUR)
            - Tages-Aggregat: `sumOutboundAmountToday()` prüft Tageslimit (GwG §3)

            **Vier-Augen-Prozess:**
            Transfers > 25.000 EUR gehen in `PENDING_APPROVAL` → ein zweiter autorisierter \
            Nutzer muss via `POST /transfers/:id/approve` bestätigen.

            **Gebühren (per `TenantSettings`, mandantenspezifisch):**
            - FX-Spread B2B: 0,15% (1,5 Bp) auf USDC-Transaktionen
            - Flat Fee B2B: 2,50 EUR | B2C: 0,50 EUR
            - Revenue-Buchung: Ertragskonto `DE00ATRUVIA0001ERTRAG` via CoreBanking
            """,
            List.of("[LimitResolver.java]", "[TenantSettings.java]",
                    "[B2bTransferService.java — executeTransferFlow]", "[GwG §3]")
        ),

        // ── FX / Wechselkurs ────────────────────────────────────────────────────
        new KnowledgeEntry(
            List.of("fx", "wechselkurs", "eur/usd", "usdc kurs", "devisen",
                    "fxrate", "ecb", "slippage"),
            """
            **FX-Kursberechnung (EUR ↔ USDC)**

            **Semantic:** `FxRateService.getBaseRate(USDC)` gibt **USDC pro 1 EUR** zurück \
            (EUR/USD-Rate, z. B. 1,0823 = 1 EUR = 1,0823 USDC).

            **Outbound (EUR → USDC):** `amountUsdc = amountEur × rate`
            **Inbound (USDC → EUR):** `amountEur = amountUsdc / rate` ← Bugfix F-01 (2026-08-20)

            **Datenquelle:**
            - **Dev:** Mock-Rate 1,0823 (statisch)
            - **Prod:** ECB SDMX-API Series `D.USD.EUR.SP00.A` (tagesaktuelle Rate)

            **Slippage-Schutz (G-06):** Bei USDC-Transfers wird die Kursabweichung zwischen \
            `RateQuote` (Angebot) und tatsächlichem Ausführungskurs geprüft. \
            EURC/EURAU/EURQ: 1:1-Parität, kein Slippage-Check nötig.
            """,
            List.of("[FxRateService.java]", "[ECB SDMX-API]",
                    "[MiCA Art. 23 Wechselkurstransparenz]")
        ),

        // ── Security / RBAC ─────────────────────────────────────────────────────
        new KnowledgeEntry(
            List.of("security", "jwt", "rbac", "authentifizierung", "sicherheit",
                    "login", "token", "rollen", "admin", "kill switch"),
            """
            **Security-Architektur (OWASP / BaFin BAIT)**

            **Authentifizierung:**
            - JWT HS256 (`jwtSecret` mind. 256-Bit, UTF-8-Charset)
            - Claims: `sub` (customerId), `tenant` (Mandant-ID), `roles` (optional)
            - `JwtAuthFilter` setzt `TenantContext` und `SecurityContextHolder` pro Request

            **RBAC:**
            - Alle Admin-Endpunkte (`/api/v1/b2b/admin/**`): `ROLE_ATRUVIA_ADMIN`
            - Dev-Token mit Admin: `GET /api/v1/auth/dev-token?admin=true`
            - Normaler B2B/B2C-Zugriff: `ROLE_USER` (Basis-Authentifizierung)

            **Sicherheitsmaßnahmen (Phase-1+2-Audit):**
            - X-Forwarded-For nur bei konfiguriertem Trusted-Proxy
            - PHONE_HMAC_KEY Startup-Guard (kein Default in Prod)
            - IBAN-Sanitierung in Content-Disposition (Header-Injection)
            - IDOR-Schutz: Ownership-Check in B2bTransferService.getById()

            **Kill-Switch (DORA Art. 17):**
            `POST /admin/kill-switch/activate?scope=GLOBAL` — stoppt alle HTTP-Requests \
            UND den OutboxProcessor.
            """,
            List.of("[SecurityConfig.java]", "[JwtAuthFilter.java]",
                    "[BAIT Kapitel 4 — IT-Sicherheit]", "[DORA Art. 17]")
        )
    );

    // ── Public API ────────────────────────────────────────────────────────────────

    public DevChatResponse answer(String message, String tenantId) {
        String lowerMsg = message.toLowerCase(Locale.GERMAN);

        // Wissensdatenbank durchsuchen
        for (KnowledgeEntry entry : KNOWLEDGE_BASE) {
            if (entry.matches(lowerMsg)) {
                String reply = personalizeReply(entry.reply(), tenantId);
                return new DevChatResponse(reply, entry.sources());
            }
        }

        // Fallback: Hilfreiche Antwort ohne passenden Eintrag
        return fallbackResponse(message, tenantId);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────────

    private String personalizeReply(String reply, String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return reply;

        try {
            TenantSettings settings = tenantSettingsService.get(tenantId);
            String tenantName = humanTenantName(tenantId);
            String prefix = String.format(
                "**[%s]** Du bist aktuell als **%s** angemeldet.\n\n",
                tenantId, tenantName);

            if (reply.contains("Vier-Augen-Schwelle") || reply.contains("limit")) {
                prefix += String.format(
                    "💡 **Eure aktuellen Limits:** Single: %s EUR · Daily: %s EUR · " +
                    "4-Augen ab: %s EUR\n\n",
                    settings.getTxLimitSingleB2b().toPlainString(),
                    settings.getTxLimitDailyB2b().toPlainString(),
                    settings.getApprovalThresholdB2b().toPlainString());
            }
            return prefix + reply;
        } catch (Exception e) {
            return reply;
        }
    }

    private DevChatResponse fallbackResponse(String message, String tenantId) {
        String tenantHint = tenantId != null && !tenantId.isBlank()
            ? " Du bist aktuell als **" + humanTenantName(tenantId) + "** angemeldet."
            : "";

        String reply = String.format("""
            Zu Ihrer Frage „%s" habe ich keinen direkten Treffer in meiner Wissensdatenbank.%s

            **Folgende Themenbereiche kenne ich gut:**
            - 🏦 **Multi-Tenancy & RLS** — Mandantentrennung der VR-Banken
            - 📤 **Outbox Pattern** — Crash-Recovery und At-Least-Once-Delivery
            - 📋 **Regulatorik** — BaFin MaRisk/BAIT, MiCA, EU TFR, GwG §43
            - 🔒 **Security** — JWT, RBAC, Kill-Switch, AML-Screening
            - 📈 **Yield/Zinsen** — B2C-Sparkonto, DATEV, Kapitalertragsteuer
            - 💱 **FX & Limits** — Wechselkursberechnung, Transaktionslimits, Vier-Augen
            - ⚡ **DvP & Token-Adapter** — Multi-Issuer, Delivery-versus-Payment
            - 🔄 **Idempotenz** — Race-Conditions, PSD2-Replay-Schutz

            Bitte formuliere Ihre Frage mit einem dieser Stichworte und ich helfe gerne weiter!
            """, message.length() > 100 ? message.substring(0, 97) + "..." : message, tenantHint);

        return new DevChatResponse(reply, List.of("[Atruvia Stablecoin Plattform — Dokumentation]"));
    }

    private String humanTenantName(String tenantId) {
        return switch (tenantId) {
            case "tenant-kleine-vb"  -> "Volksbank Kleinstadt eG";
            case "tenant-grosse-vb"  -> "Volksbank Metropole eG";
            case "tenant-marktbank"  -> "Marktbank AG";
            case "tenant-default"    -> "Default Dev Tenant";
            default -> tenantId;
        };
    }
}
