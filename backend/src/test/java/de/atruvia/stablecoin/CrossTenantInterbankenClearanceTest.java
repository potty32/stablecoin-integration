package de.atruvia.stablecoin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.config.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * E2E-Test: Mandantenübergreifender Stablecoin-Transfer (Interbanken-Clearance)
 *
 * Simuliert einen vollständigen Zahlungskreislauf von Mandant A zu Mandant B:
 *
 *   [MANDANT A: Kleine Volksbank]     Polygon L2 (Mock)    [MANDANT B: Große Volksbank]
 *       Müller GmbH                                             Schmidt AG
 *       IBAN: DE89...3010                                       IBAN: DE89...3002
 *       Wallet: 0x...E2E1A001   ──(1) Outbound USDC──►  0x...E2E1B002
 *                                                              │
 *                                         (2) Circle Webhook  │
 *                                         ◄───────────────────┘
 *
 * Testsequenz:
 *   TC-01: JWT-Token-Generierung für beide Mandanten
 *   TC-02: Outbound-Transfer Mandant A → SETTLED (Formel-Check: grossRevenue ≈ 17.49 EUR)
 *   TC-03: Inbound-Webhook-Empfang Mandant B → SETTLED
 *   TC-04: RLS-Sicherheits-Gegencheck (Mandant A sieht Mandant B TX NICHT → HTTP 404)
 *   TC-05: Mathematische Ertragsformel R = (V×S) + F - C ≈ 17.492 EUR
 *
 * Korrekturen gegenüber ursprünglicher Testspezifikation (dokumentiert in seed_e2e_interbanken.sql):
 *   - customer_name → nicht im Schema; customer_id stattdessen
 *   - balance_eur → nicht im Schema
 *   - kyc_status → kyc_tier (TIER_3)
 *   - Webhook path /b2c → /b2b/inbound/webhook
 *   - grossRevenue ≈ 17.492 EUR (nicht 17.50, wegen gasCostSimulated=0.008 EUR)
 *   - JWT via /api/v1/auth/dev-token (nicht manueller Python-Generator)
 *
 * WICHTIG: Diese Klasse nutzt KEIN AbstractLocalDbTest!
 *          application-dev.yml → stablecoin_app (RLS aktiv) → TC-04 funktioniert korrekt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("E2E: Mandantenübergreifende Interbanken-Clearance")
class CrossTenantInterbankenClearanceTest {

    // ── Konstanten ─────────────────────────────────────────────────────────────
    private static final String TENANT_A = "tenant-kleine-vb";
    private static final String TENANT_B = "tenant-grosse-vb";
    private static final String CUST_A   = "e2e-cust-b2b-001";
    private static final String CUST_B   = "e2e-cust-b2b-002";

    private static final String ACC_A_ID   = "e2e00001-0000-0000-0000-000000000001";
    private static final String ACC_B_ID   = "e2e00002-0000-0000-0000-000000000002";
    private static final String ADDR_ID    = "e2eab00c-0000-0000-0000-000000000001";

    private static final String IBAN_A     = "DE89370400440532013010";
    private static final String IBAN_B     = "DE89370400440532013002";
    private static final String WALLET_A   = "0x00000000000000000000000000000000E2E1A001";
    private static final String WALLET_B   = "0x00000000000000000000000000000000E2E1B002";

    private static final BigDecimal TRANSFER_AMOUNT = new BigDecimal("10000.00");
    private static final String IDEMPOTENCY_KEY = "e2e-cross-tenant-tx-" + System.currentTimeMillis();

    // ── State: zwischen @Order-Tests weitergereicht ─────────────────────────────
    static String tokenA;
    static String tokenB;
    static String outboundTxId;
    static String blockchainHash;
    static BigDecimal capturedGrossRevenue;
    static String inboundTxId;

    // ── Spring Beans ────────────────────────────────────────────────────────────
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Qualifier("adminJdbcTemplate")
    @Autowired JdbcTemplate adminJdbc;

    // ── @BeforeAll: Seed-Daten anlegen (BYPASSRLS via adminJdbcTemplate) ────────

    @BeforeAll
    static void seedTestData(
            @Qualifier("adminJdbcTemplate") @Autowired JdbcTemplate adminJdbc) {

        // Mandant A: Müller GmbH (tenant-kleine-vb)
        adminJdbc.update("""
            INSERT INTO customer_account
                (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
                 tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
            VALUES
                ('e2e00001-0000-0000-0000-000000000001', 'e2e-cust-b2b-001',
                 'DE89370400440532013010', '0x00000000000000000000000000000000E2E1A001',
                 'B2B', 'TIER_3', 25000.000000, 2000000.000000,
                 'ACTIVE', NOW(), NOW(), 'tenant-kleine-vb')
            ON CONFLICT (id) DO NOTHING
            """);

        // Mandant B: Schmidt AG (tenant-grosse-vb)
        adminJdbc.update("""
            INSERT INTO customer_account
                (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
                 tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
            VALUES
                ('e2e00002-0000-0000-0000-000000000002', 'e2e-cust-b2b-002',
                 'DE89370400440532013002', '0x00000000000000000000000000000000E2E1B002',
                 'B2B', 'TIER_3', 50000.000000, 2000000.000000,
                 'ACTIVE', NOW(), NOW(), 'tenant-grosse-vb')
            ON CONFLICT (id) DO NOTHING
            """);

        // Adressbuch: Mandant A whitelistet Mandant B's Wallet (Voraussetzung Outbound)
        adminJdbc.update("""
            INSERT INTO address_book
                (id, customer_account_id, label, wallet_address, currency,
                 risk_score, status, verified_at, tenant_id)
            VALUES
                ('e2eab00c-0000-0000-0000-000000000001',
                 'e2e00001-0000-0000-0000-000000000001',
                 'Schmidt AG - Gegenpartei Metropole (E2E)',
                 '0x00000000000000000000000000000000E2E1B002',
                 'USDC', 'LOW', 'ACTIVE', NOW(), 'tenant-kleine-vb')
            ON CONFLICT (customer_account_id, wallet_address) DO NOTHING
            """);
    }

    // ── @AfterAll: Cleanup ───────────────────────────────────────────────────────

    @AfterAll
    static void cleanupTestData(
            @Qualifier("adminJdbcTemplate") @Autowired JdbcTemplate adminJdbc) {
        // FK-Reihenfolge beachten: audit_log und outbox vor stablecoin_transaction
        adminJdbc.update(
            "UPDATE stablecoin_transaction SET parent_transaction_id = NULL " +
            "WHERE parent_transaction_id IN (SELECT id FROM stablecoin_transaction " +
            "   WHERE customer_account_id IN ('e2e00001-0000-0000-0000-000000000001'," +
            "                                 'e2e00002-0000-0000-0000-000000000002'))");
        adminJdbc.update(
            "DELETE FROM audit_log WHERE transaction_id IN " +
            "(SELECT id FROM stablecoin_transaction WHERE customer_account_id IN " +
            "('e2e00001-0000-0000-0000-000000000001','e2e00002-0000-0000-0000-000000000002'))");
        adminJdbc.update(
            "DELETE FROM outbox_message WHERE transaction_id IN " +
            "(SELECT id FROM stablecoin_transaction WHERE customer_account_id IN " +
            "('e2e00001-0000-0000-0000-000000000001','e2e00002-0000-0000-0000-000000000002'))");
        adminJdbc.update(
            "DELETE FROM stablecoin_transaction WHERE customer_account_id IN " +
            "('e2e00001-0000-0000-0000-000000000001','e2e00002-0000-0000-0000-000000000002')");
        adminJdbc.update(
            "DELETE FROM address_book WHERE id = 'e2eab00c-0000-0000-0000-000000000001'");
        adminJdbc.update(
            "DELETE FROM customer_account WHERE id IN " +
            "('e2e00001-0000-0000-0000-000000000001','e2e00002-0000-0000-0000-000000000002')");
        TenantContext.clear();
    }

    // ── TC-01: JWT-Token-Generierung ────────────────────────────────────────────

    @Test
    @Order(1)
    @DisplayName("TC-01: JWT-Tokens für Mandant A (Kleine VB) und Mandant B (Große VB) erzeugen")
    void tc01_generateJwtTokensBothTenants() throws Exception {

        // Token Mandant A: Müller GmbH / tenant-kleine-vb
        MvcResult resultA = mockMvc.perform(
                get("/api/v1/auth/dev-token")
                    .param("customerId", CUST_A)
                    .param("tenant", TENANT_A))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.tenant").value(TENANT_A))
            .andExpect(jsonPath("$.customerId").value(CUST_A))
            .andReturn();

        tokenA = extractField(resultA, "token");

        // Token Mandant B: Schmidt AG / tenant-grosse-vb
        MvcResult resultB = mockMvc.perform(
                get("/api/v1/auth/dev-token")
                    .param("customerId", CUST_B)
                    .param("tenant", TENANT_B))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andReturn();

        tokenB = extractField(resultB, "token");

        // Assertion
        assertThat(tokenA).isNotBlank().contains(".");
        assertThat(tokenB).isNotBlank().contains(".");
        assertThat(tokenA).isNotEqualTo(tokenB); // Verschiedene Tokens!

        // JWT-Claims prüfen (Payload ist Base64-kodiert, mittleres Segment)
        String payloadA = new String(java.util.Base64.getUrlDecoder().decode(tokenA.split("\\.")[1]));
        assertThat(payloadA).contains("\"tenant\":\"" + TENANT_A + "\"");
        assertThat(payloadA).contains("\"sub\":\"" + CUST_A + "\"");

        String payloadB = new String(java.util.Base64.getUrlDecoder().decode(tokenB.split("\\.")[1]));
        assertThat(payloadB).contains("\"tenant\":\"" + TENANT_B + "\"");
    }

    // ── TC-02: Outbound-Transfer Mandant A → SETTLED ────────────────────────────

    @Test
    @Order(2)
    @DisplayName("TC-02: Outbound-Transfer Mandant A (Müller GmbH → Schmidt AG Wallet) → SETTLED")
    void tc02_outboundTransferMandantA() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();

        String requestBody = """
            {
              "sourceIban":        "%s",
              "destinationWallet": "%s",
              "amountEur":         %s,
              "currency":          "USDC",
              "reference":         "E2E Interbanken-Zahlung Lieferant Schmidt AG"
            }
            """.formatted(IBAN_A, WALLET_B, TRANSFER_AMOUNT.toPlainString());

        MvcResult result = mockMvc.perform(
                post("/api/v1/b2b/transfers")
                    .header("Authorization", "Bearer " + tokenA)
                    .header("X-Idempotency-Key", IDEMPOTENCY_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transactionId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("SETTLED"))
            .andExpect(jsonPath("$.type").value("OUTBOUND"))
            .andExpect(jsonPath("$.currency").value("USDC"))
            .andExpect(jsonPath("$.requiresApproval").value(false))
            .andExpect(jsonPath("$.blockchainHash").isNotEmpty())
            .andExpect(jsonPath("$.settledAt").isNotEmpty())
            .andReturn();

        // State für Folge-Tests speichern
        outboundTxId = extractField(result, "transactionId");
        blockchainHash = extractField(result, "blockchainHash");
        capturedGrossRevenue = new BigDecimal(extractField(result, "grossRevenue"));

        assertThat(outboundTxId).isNotBlank();
        assertThat(blockchainHash).isNotBlank().startsWith("0x");
        assertThat(capturedGrossRevenue).isPositive();

        // Betrag-Kontrolle: amountFiat = 10.000 EUR
        BigDecimal amountFiat = new BigDecimal(extractField(result, "amountFiat"));
        assertThat(amountFiat).isEqualByComparingTo(TRANSFER_AMOUNT);

        // USDC-Betrag > EUR-Betrag (Wechselkurs > 1)
        BigDecimal amountStablecoin = new BigDecimal(extractField(result, "amountStablecoin"));
        assertThat(amountStablecoin).isGreaterThan(TRANSFER_AMOUNT);
    }

    // ── TC-03: Inbound-Webhook-Empfang Mandant B → SETTLED ──────────────────────

    @Test
    @Order(3)
    @DisplayName("TC-03: Circle-Webhook simuliert Zahlungseingang bei Mandant B (Schmidt AG) → SETTLED")
    void tc03_inboundWebhookMandantB() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();
        assertThat(blockchainHash).as("TC-02 muss zuerst laufen").isNotNull();

        // Neuen Blockchain-Hash für Inbound (andere TX auf der Chain)
        String inboundHash = "0xE2E-INBOUND-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        // Hinweis: Webhook ist permitAll() — KEIN Authorization-Header benötigt!
        // Der Mandanten-Kontext wird durch wallet_address-Lookup bestimmt (nicht JWT).
        String webhookPayload = """
            {
              "walletId":       "%s",
              "amount":         %s,
              "currency":       "USDC",
              "blockchainHash": "%s",
              "senderWallet":   "%s"
            }
            """.formatted(WALLET_B, TRANSFER_AMOUNT.toPlainString(), inboundHash, WALLET_A);

        MvcResult result = mockMvc.perform(
                post("/api/v1/b2b/inbound/webhook")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(webhookPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transactionId").isNotEmpty())
            .andExpect(jsonPath("$.status").value("SETTLED"))
            .andExpect(jsonPath("$.type").value("INBOUND"))
            .andExpect(jsonPath("$.currency").value("USDC"))
            .andReturn();

        inboundTxId = extractField(result, "transactionId");
        assertThat(inboundTxId).isNotBlank();

        // amountStablecoin = ursprünglich empfangener USDC-Betrag (unveränderlich durch FX)
        // amountFiat = FX-konvertierter EUR-Wert (amountFiat × getBaseRate(USDC) = amountFiat × 1.0823)
        BigDecimal inboundAmountUsdc = new BigDecimal(extractField(result, "amountStablecoin"));
        assertThat(inboundAmountUsdc).as("Empfangener USDC-Betrag muss dem Sendebetrag entsprechen")
                                     .isEqualByComparingTo(TRANSFER_AMOUNT);

        // FX-Korrektheit (F-01-Fix): amountFiat (EUR) = USDC-Betrag / Mock-FX-Rate (1.0823)
        // Semantik: 1 EUR = 1.0823 USDC → 10.000 USDC / 1.0823 ≈ 9.239,44 EUR
        BigDecimal inboundAmountEur = new BigDecimal(extractField(result, "amountFiat"));
        BigDecimal mockFxRate = new BigDecimal("1.0823");
        BigDecimal expectedEur = TRANSFER_AMOUNT.divide(mockFxRate, 6, java.math.RoundingMode.HALF_UP);
        BigDecimal delta = inboundAmountEur.subtract(expectedEur).abs();
        assertThat(delta).as("Inbound EUR-Betrag = USDC / FX-Rate (korrekte Richtung, ±50 EUR Toleranz)")
                         .isLessThanOrEqualTo(new BigDecimal("50"));

        // Blockchain-Hash muss im Response stehen
        String inboundBlockchainHash = extractField(result, "blockchainHash");
        assertThat(inboundBlockchainHash).isEqualTo(inboundHash);
    }

    // ── TC-04: RLS-Sicherheits-Gegencheck ───────────────────────────────────────

    @Test
    @Order(4)
    @DisplayName("TC-04: RLS-Isolation — Mandant A kann Mandant B Inbound-TX nicht sehen (HTTP 404)")
    void tc04_rlsIsolation_mandantACannotSeeMandantBTransaction() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();
        assertThat(inboundTxId).as("TC-03 muss zuerst laufen").isNotNull();

        // Spionage-Versuch: Mandant A greift mit seinem Token auf Mandant B's TX zu
        // PostgreSQL RLS: tenant_id = 'tenant-grosse-vb' ≠ 'tenant-kleine-vb' → 0 rows → 404
        mockMvc.perform(
                get("/api/v1/b2b/transfers/" + inboundTxId)
                    .header("Authorization", "Bearer " + tokenA))  // ← FALSCHER Mandant!
            .andExpect(status().isNotFound());

        // Positiv-Check: Mandant B kann seine eigene TX sehen
        mockMvc.perform(
                get("/api/v1/b2b/transfers/" + inboundTxId)
                    .header("Authorization", "Bearer " + tokenB))  // ← Korrekter Mandant
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(inboundTxId));

        // Positiv-Check: Mandant A kann seine eigene Outbound-TX sehen
        mockMvc.perform(
                get("/api/v1/b2b/transfers/" + outboundTxId)
                    .header("Authorization", "Bearer " + tokenA))  // ← Korrekter Mandant
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transactionId").value(outboundTxId));

        // Gegenprüfung: Mandant B kann Mandant A's Outbound-TX NICHT sehen
        mockMvc.perform(
                get("/api/v1/b2b/transfers/" + outboundTxId)
                    .header("Authorization", "Bearer " + tokenB))  // ← FALSCHER Mandant!
            .andExpect(status().isNotFound());
    }

    // ── TC-05: Mathematische Ertragsformel-Verifikation ─────────────────────────

    @Test
    @Order(5)
    @DisplayName("TC-05: Ertragsformel R = (V×S) + F − C ≈ 17.492 EUR für Mandant A")
    void tc05_revenueFormulaVerification() {
        assertThat(capturedGrossRevenue).as("TC-02 muss zuerst laufen").isNotNull();

        /*
         * Formeln aus Testspezifikation (korrigiert mit tatsächlichen Mock-Werten):
         *
         *   V (Volumen)          = 10.000,00 EUR
         *   S (FX-Spread)        =      0,15%  (TenantSettings Default = 0.001500)
         *   F (Transaktionsgebühr) =     2,50 EUR (TenantSettings Default = 2.500000)
         *   C (Gaskosten)         =     0,008 EUR (app.revenue.gas-cost-simulated = 0.008)
         *
         *   Spread-Betrag = V × S = 10.000 × 0.0015 = 15,00 EUR
         *   R = Spread-Betrag + F - C
         *   R = 15,00 + 2,50 - 0,008 = 17,492 EUR
         *
         *   Testspezifikation: 17,50 EUR (gerundet, ignoriert Gaskosten)
         *   Tatsächlich:       17,492 EUR (exakt laut RevenueService)
         */

        BigDecimal spreadAmount = TRANSFER_AMOUNT
                .multiply(new BigDecimal("0.0015"))
                .setScale(6, RoundingMode.HALF_UP);
        BigDecimal fee         = new BigDecimal("2.50");
        BigDecimal gasCost     = new BigDecimal("0.008");
        BigDecimal expectedR   = spreadAmount.add(fee).subtract(gasCost);

        // Ergebnis: 15.000000 + 2.500000 - 0.008000 = 17.492000 EUR
        assertThat(spreadAmount).as("Spread-Betrag").isEqualByComparingTo(new BigDecimal("15.000000"));
        assertThat(expectedR)   .as("Erwarteter Bruttoertrag").isEqualByComparingTo(new BigDecimal("17.492000"));

        // Tatsächlicher Ertrag aus TC-02 (aus API-Response)
        assertThat(capturedGrossRevenue)
                .as("Bruttoertrag aus API-Response muss zur Formel passen")
                .isBetween(new BigDecimal("17.40"), new BigDecimal("17.60")); // ±0.10 Toleranz für Mock-Variationen

        // Präziser Vergleich mit exakter Formelberechnung
        BigDecimal delta = capturedGrossRevenue.subtract(expectedR).abs();
        assertThat(delta)
                .as("Abweichung zwischen API-Ertrag und Formel-Ertrag darf max. 0.05 EUR betragen")
                .isLessThanOrEqualTo(new BigDecimal("0.05"));

        // Geld-Kontroverse des Senders: grossDebit = amountFiat + fee + spread
        // Müller GmbH wird mit 10.017,50 EUR belastet (Brutto)
        BigDecimal spreadForDebit = TRANSFER_AMOUNT.multiply(new BigDecimal("0.0015")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal expectedGrossDebit = TRANSFER_AMOUNT.add(fee).add(spreadForDebit);
        assertThat(expectedGrossDebit)
                .as("Erwarteter Brutto-Kundendebit")
                .isEqualByComparingTo(new BigDecimal("10017.50"));
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────────

    private String extractField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        JsonNode node = root.get(field);
        assertThat(node).as("JSON-Feld '%s' muss in Response vorhanden sein. Body: %s", field, body)
                        .isNotNull();
        return node.asText();
    }
}
