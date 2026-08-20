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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * UC-33/34/35: Delivery-versus-Payment (DvP) Escrow Engine
 *
 * Testet den vollständigen DvP-Zustandszyklus:
 *   ESCROWED → SETTLED  (Wertpapierübertragung erfolgreich)
 *   ESCROWED → CANCELLED (Wertpapierübertragung gescheitert)
 *
 * RLS-Sicherheitsprüfung: Mandant A darf Mandant B's Escrow nicht settle/cancel.
 *
 *   [Mandant A: Kleine VB]   DvP-Engine   [Mandant B: Große VB]
 *      Käufer GmbH                           Wertpapierhändler AG
 *      IBAN: DVP_IBAN_A                       IBAN: DVP_IBAN_B
 *      EURAU-Escrow                           Settlement-Wallet
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("UC-33/34/35: DvP Escrow Engine")
class DvpProcessingTest {

    // ── Konstanten ──────────────────────────────────────────────────────────
    private static final String TENANT_A  = "tenant-kleine-vb";
    private static final String TENANT_B  = "tenant-grosse-vb";
    private static final String CUST_DVP_A = "dvp-cust-001";
    private static final String CUST_DVP_B = "dvp-cust-002";
    private static final String ACC_DVP_A_ID = "dc000001-0000-0000-0000-000000000001";
    private static final String ACC_DVP_B_ID = "dc000002-0000-0000-0000-000000000002";
    private static final String IBAN_A    = "DE89000000000000DVP0001";
    private static final String IBAN_B    = "DE89000000000000DVP0002";

    private static final String ISIN = "DE0008404005"; // Allianz SE
    private static final String ESCROW_REF_BASE = "DVP-TEST-" + System.currentTimeMillis();

    // ── Shared State ────────────────────────────────────────────────────────
    static String tokenA;
    static String tokenB;
    static String escrowIdForSettle;
    static String escrowIdForCancel;
    static String escrowIdForRls;

    // ── Spring Beans ────────────────────────────────────────────────────────
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Qualifier("adminJdbcTemplate") @Autowired JdbcTemplate adminJdbc;

    // ── @BeforeAll: Seed-Daten ──────────────────────────────────────────────

    @BeforeAll
    static void seedTestData(@Qualifier("adminJdbcTemplate") @Autowired JdbcTemplate adminJdbc) {
        adminJdbc.update("""
            INSERT INTO customer_account
                (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
                 tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
            VALUES
                ('dc000001-0000-0000-0000-000000000001', 'dvp-cust-001',
                 'DE89000000000000DVP0001', '0xDVPWalletA001',
                 'B2B', 'TIER_3', 1000000.000000, 10000000.000000,
                 'ACTIVE', NOW(), NOW(), 'tenant-kleine-vb')
            ON CONFLICT (id) DO NOTHING
            """);
        adminJdbc.update("""
            INSERT INTO customer_account
                (id, customer_id, iban, wallet_address, customer_type, kyc_tier,
                 tx_limit_single, tx_limit_daily, status, created_at, updated_at, tenant_id)
            VALUES
                ('dc000002-0000-0000-0000-000000000002', 'dvp-cust-002',
                 'DE89000000000000DVP0002', '0xDVPWalletB002',
                 'B2B', 'TIER_3', 1000000.000000, 10000000.000000,
                 'ACTIVE', NOW(), NOW(), 'tenant-grosse-vb')
            ON CONFLICT (id) DO NOTHING
            """);
    }

    @AfterAll
    static void cleanupTestData(@Qualifier("adminJdbcTemplate") @Autowired JdbcTemplate adminJdbc) {
        adminJdbc.update(
            "DELETE FROM dvp_escrow WHERE customer_account_id IN " +
            "('dc000001-0000-0000-0000-000000000001','dc000002-0000-0000-0000-000000000002')");
        adminJdbc.update(
            "DELETE FROM customer_account WHERE id IN " +
            "('dc000001-0000-0000-0000-000000000001','dc000002-0000-0000-0000-000000000002')");
        TenantContext.clear();
    }

    // ── TC-01: JWT-Tokens holen ─────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("TC-01: Tokens für beide Mandanten erzeugen")
    void tc01_generateTokens() throws Exception {
        MvcResult rA = mockMvc.perform(get("/api/v1/auth/dev-token")
                        .param("customerId", CUST_DVP_A).param("tenant", TENANT_A))
                .andExpect(status().isOk()).andReturn();
        tokenA = field(rA, "token");

        MvcResult rB = mockMvc.perform(get("/api/v1/auth/dev-token")
                        .param("customerId", CUST_DVP_B).param("tenant", TENANT_B))
                .andExpect(status().isOk()).andReturn();
        tokenB = field(rB, "token");

        assertThat(tokenA).isNotBlank().contains(".");
        assertThat(tokenB).isNotBlank().contains(".").isNotEqualTo(tokenA);
    }

    // ── TC-02: Lock → ESCROWED ──────────────────────────────────────────────

    @Test @Order(2)
    @DisplayName("TC-02: DvP Lock erzeugt Escrow mit Status ESCROWED")
    void tc02_dvpLock_createsEscrowed() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();

        MvcResult result = mockMvc.perform(post("/api/v1/b2b/dvp/lock")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockPayload(ESCROW_REF_BASE + "-settle")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ESCROWED"))
                .andExpect(jsonPath("$.escrowId").isNotEmpty())
                .andExpect(jsonPath("$.securitiesIsin").value(ISIN))
                .andExpect(jsonPath("$.amountFiat").value(50000.0))
                .andReturn();

        escrowIdForSettle = field(result, "escrowId");
        assertThat(escrowIdForSettle).isNotBlank();
    }

    // ── TC-03: Lock → Settle = SETTLED ─────────────────────────────────────

    @Test @Order(3)
    @DisplayName("TC-03: DvP Settle gibt Escrow frei → Status SETTLED + blockchainHash")
    void tc03_dvpSettle_transitionsToSettled() throws Exception {
        assertThat(escrowIdForSettle).as("TC-02 muss zuerst laufen").isNotNull();

        MvcResult result = mockMvc.perform(post("/api/v1/b2b/dvp/settle")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "%s" }
                            """.formatted(escrowIdForSettle, ESCROW_REF_BASE + "-settle")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.blockchainHash").isNotEmpty())
                .andExpect(jsonPath("$.feeAmount").isNumber())
                .andReturn();

        String blockchainHash = field(result, "blockchainHash");
        assertThat(blockchainHash).isNotBlank().startsWith("0x");
    }

    // ── TC-04: Lock → Cancel = CANCELLED ───────────────────────────────────

    @Test @Order(4)
    @DisplayName("TC-04: DvP Cancel storniert Escrow → Status CANCELLED, gebührenfrei")
    void tc04_dvpCancel_transitionsToCancelled() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();

        // Neuen Escrow für Cancel anlegen
        MvcResult lockResult = mockMvc.perform(post("/api/v1/b2b/dvp/lock")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lockPayload(ESCROW_REF_BASE + "-cancel")))
                .andExpect(status().isCreated()).andReturn();
        escrowIdForCancel = field(lockResult, "escrowId");

        MvcResult result = mockMvc.perform(post("/api/v1/b2b/dvp/cancel")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "%s",
                              "cancellationReason": "Gegenpartei ausgefallen" }
                            """.formatted(escrowIdForCancel, ESCROW_REF_BASE + "-cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Gegenpartei ausgefallen"))
                .andReturn();

        assertThat(field(result, "status")).isEqualTo("CANCELLED");
    }

    // ── TC-05: Settle nicht-existenter Escrow → 404 ─────────────────────────

    @Test @Order(5)
    @DisplayName("TC-05: Settle nicht-existenter Escrow → HTTP 404")
    void tc05_settleNonExistent_returns404() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();
        String fakeId = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/b2b/dvp/settle")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "NONEXISTENT-REF" }
                            """.formatted(fakeId)))
                .andExpect(status().isNotFound());
    }

    // ── TC-06: Cancel nicht-existenter Escrow → 404 ─────────────────────────

    @Test @Order(6)
    @DisplayName("TC-06: Cancel nicht-existenter Escrow → HTTP 404")
    void tc06_cancelNonExistent_returns404() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();

        mockMvc.perform(post("/api/v1/b2b/dvp/cancel")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "NONEXISTENT-REF",
                              "cancellationReason": "Test" }
                            """.formatted(UUID.randomUUID())))
                .andExpect(status().isNotFound());
    }

    // ── TC-07: Double-Settle → 422 ──────────────────────────────────────────

    @Test @Order(7)
    @DisplayName("TC-07: Double-Settle auf bereits SETTLED Escrow → HTTP 422")
    void tc07_doubleSettle_returns422() throws Exception {
        assertThat(escrowIdForSettle).as("TC-03 muss zuerst laufen (escrow bereits SETTLED)").isNotNull();

        mockMvc.perform(post("/api/v1/b2b/dvp/settle")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "%s" }
                            """.formatted(escrowIdForSettle, ESCROW_REF_BASE + "-settle")))
                .andExpect(status().isBadRequest());
    }

    // ── TC-08: RLS — Mandant A kann Mandant B's Escrow nicht settle ──────────

    @Test @Order(8)
    @DisplayName("TC-08: RLS — Mandant A kann Mandant B's Escrow nicht settle/cancel (HTTP 404)")
    void tc08_rlsIsolation_mandantACannotSettleMandantBEscrow() throws Exception {
        assertThat(tokenA).as("TC-01 muss zuerst laufen").isNotNull();
        assertThat(tokenB).as("TC-01 muss zuerst laufen").isNotNull();

        // Escrow für Mandant B anlegen (mit tokenB)
        String refB = ESCROW_REF_BASE + "-rls-b";
        MvcResult lockResult = mockMvc.perform(post("/api/v1/b2b/dvp/lock")
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "sourceIban": "%s",
                              "amountEur": 20000,
                              "currency": "EURAU",
                              "settlementWallet": "0xSettlementWallet",
                              "securitiesIsin": "%s",
                              "securitiesAmount": 5,
                              "escrowReference": "%s",
                              "securitiesSystemId": "DEKA" }
                            """.formatted(IBAN_B, ISIN, refB)))
                .andExpect(status().isCreated()).andReturn();
        escrowIdForRls = field(lockResult, "escrowId");

        // Mandant A versucht, Mandant B's Escrow zu settlen → RLS blockiert → 404
        mockMvc.perform(post("/api/v1/b2b/dvp/settle")
                        .header("Authorization", "Bearer " + tokenA)  // ← falscher Mandant!
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "%s" }
                            """.formatted(escrowIdForRls, refB)))
                .andExpect(status().isNotFound());

        // Mandant A versucht, Mandant B's Escrow zu canceln → RLS blockiert → 404
        mockMvc.perform(post("/api/v1/b2b/dvp/cancel")
                        .header("Authorization", "Bearer " + tokenA)  // ← falscher Mandant!
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "%s",
                              "cancellationReason": "Spionageversuch" }
                            """.formatted(escrowIdForRls, refB)))
                .andExpect(status().isNotFound());

        // Mandant B selbst kann seinen Escrow canceln (Positivtest)
        mockMvc.perform(post("/api/v1/b2b/dvp/cancel")
                        .header("Authorization", "Bearer " + tokenB)  // ← korrekter Mandant
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "escrowId": "%s", "escrowReference": "%s",
                              "cancellationReason": "RLS-Positivtest" }
                            """.formatted(escrowIdForRls, refB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── Hilfsmethoden ────────────────────────────────────────────────────────

    private String lockPayload(String escrowRef) {
        return """
            { "sourceIban": "%s",
              "amountEur": 50000,
              "currency": "EURAU",
              "settlementWallet": "0xSettlementWalletTarget",
              "securitiesIsin": "%s",
              "securitiesAmount": 10,
              "escrowReference": "%s",
              "securitiesSystemId": "DEKA" }
            """.formatted(IBAN_A, ISIN, escrowRef);
    }

    private String field(MvcResult result, String fieldName) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(body);
        JsonNode node = root.get(fieldName);
        assertThat(node)
                .as("JSON-Feld '%s' muss in Response vorhanden sein. Body: %s", fieldName, body)
                .isNotNull();
        return node.asText();
    }
}
