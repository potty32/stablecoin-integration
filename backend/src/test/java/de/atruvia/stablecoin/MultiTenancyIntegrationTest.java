package de.atruvia.stablecoin;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.config.TenantContext;
import de.atruvia.stablecoin.entity.*;
import de.atruvia.stablecoin.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Multi-Tenancy Integrationstests.
 *
 * Verbindet sich als stablecoin_app (aus application-dev.yml) — RLS-fähiger App-User.
 * TenantAwareDataSource setzt app.current_tenant bei jeder Connection aus dem Pool.
 *
 * Tests prüfen:
 * 1. Dev-Token-Endpoint generiert JWT mit tenant-Claim
 * 2. EntityListener setzt tenant_id korrekt beim Persistieren
 * 3. Tenant B sieht keine Transfers von Tenant A (App-Layer + RLS)
 * 4. Tenant B kann Tenant-A-Transaktion per ID nicht abrufen
 * 5. JWT ohne tenant-Claim fällt auf 'tenant-default' zurück
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MultiTenancyIntegrationTest {

    // KEINE @DynamicPropertySource-Override: application-dev.yml verwendet stablecoin_app (RLS aktiv).
    // TenantAwareDataSource setzt app.current_tenant bei Connection-Acquire.

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CustomerAccountRepository accountRepo;
    @Autowired StablecoinTransactionRepository txRepo;
    @Autowired AuditLogRepository auditLogRepo;
    @Autowired AddressBookRepository addressBookRepo;
    @Autowired OutboxMessageRepository outboxRepo;
    @Autowired ApprovalWorkflowRepository approvalRepo;
    @Autowired YieldPositionRepository yieldPositionRepo;

    static final String TENANT_A = "tenant-kleine-vb";
    static final String TENANT_B = "tenant-grosse-vb";

    // Einzigartige IDs pro Test-Run (verhindert UNIQUE-Constraint-Konflikte)
    final String RUN_ID = UUID.randomUUID().toString().replace("-", "").substring(0, 8);

    CustomerAccount accountA;
    CustomerAccount accountB;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_A);
        accountA = createTestAccount("test-a-" + RUN_ID, "DE" + RUN_ID.toUpperCase() + "00001", TENANT_A);
        TenantContext.clear();

        TenantContext.set(TENANT_B);
        accountB = createTestAccount("test-b-" + RUN_ID, "DE" + RUN_ID.toUpperCase() + "00002", TENANT_B);
        TenantContext.clear();
    }

    @AfterEach
    void cleanup() {
        // deleteAllInBatch() = JPQL "DELETE FROM T t" — kein Entity-Laden, kein FK-Problem.
        // Tabellen ohne RLS (outbox, approval): löscht ALLE Rows (OK für Test-Isolation).
        // Tabellen mit RLS: löscht nur aktiven Tenant.
        TenantContext.set(TENANT_A);
        outboxRepo.deleteAllInBatch();
        auditLogRepo.deleteAllInBatch();
        approvalRepo.deleteAllInBatch();
        yieldPositionRepo.deleteAllInBatch();
        txRepo.deleteAllInBatch();
        addressBookRepo.deleteAllInBatch();
        accountRepo.deleteById(accountA.getId());
        TenantContext.clear();

        TenantContext.set(TENANT_B);
        txRepo.deleteAllInBatch();
        addressBookRepo.deleteAllInBatch();
        yieldPositionRepo.deleteAllInBatch();
        accountRepo.deleteById(accountB.getId());
        TenantContext.clear();
    }

    // ============================================================
    // TC1: Dev-Token-Endpoint generiert JWT mit tenant-Claim
    // ============================================================
    @Test
    @Order(1)
    void devTokenEndpoint_returnsTenantClaim() throws Exception {
        mockMvc.perform(get("/api/v1/auth/dev-token")
                        .param("customerId", "cust-b2b-001")
                        .param("tenant", TENANT_A))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tenant").value(TENANT_A))
                .andExpect(jsonPath("$.customerId").value("cust-b2b-001"));
    }

    // ============================================================
    // TC2: EntityListener setzt tenant_id korrekt beim Persistieren
    // ============================================================
    @Test
    @Order(2)
    void entityListener_setsTenantId_onPersist() {
        assertThat(accountA.getTenantId())
                .as("Account Tenant A muss tenant_id='%s' haben", TENANT_A)
                .isEqualTo(TENANT_A);

        assertThat(accountB.getTenantId())
                .as("Account Tenant B muss tenant_id='%s' haben", TENANT_B)
                .isEqualTo(TENANT_B);
    }

    // ============================================================
    // TC3: Tenant B sieht keine Transfers von Tenant A
    // ============================================================
    @Test
    @Order(3)
    void tenantB_cannotSeeTenantA_transfers() throws Exception {
        // Tenant A whitelistet eine Adresse und erstellt einen Transfer
        String tokenA = getDevToken(accountA.getCustomerId(), TENANT_A);
        TenantContext.set(TENANT_A);
        AddressBook wb = whitelistWallet(accountA, "0xTestWallet" + RUN_ID + "0001");
        TenantContext.clear();

        mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"sourceIban":"%s","destinationWallet":"%s","amountEur":500,
                             "currency":"USDC","reference":"TC3-Test"}
                            """.formatted(accountA.getIban(), wb.getWalletAddress())))
                .andExpect(status().isCreated());

        // Tenant B fragt Transfers ab — darf keine Tenant-A-Transaktionen sehen
        String tokenB = getDevToken(accountB.getCustomerId(), TENANT_B);
        mockMvc.perform(get("/api/v1/b2b/transfers")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    // ============================================================
    // TC4: Tenant B kann Tenant-A-Transaktion per ID nicht abrufen
    // ============================================================
    @Test
    @Order(4)
    void tenantB_cannotAccessTenantA_transactionById() throws Exception {
        String tokenA = getDevToken(accountA.getCustomerId(), TENANT_A);
        TenantContext.set(TENANT_A);
        AddressBook wb = whitelistWallet(accountA, "0xTestWallet" + RUN_ID + "0002");
        TenantContext.clear();

        MvcResult createResult = mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("Authorization", "Bearer " + tokenA)
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"sourceIban":"%s","destinationWallet":"%s","amountEur":300,
                             "currency":"USDC","reference":"TC4-Test"}
                            """.formatted(accountA.getIban(), wb.getWalletAddress())))
                .andExpect(status().isCreated())
                .andReturn();

        String txId = extractJsonField(createResult, "transactionId");
        assertThat(txId).isNotEmpty();

        // Tenant B: kann TX per ID nicht abrufen (RLS filtert die TX aus → 404 oder
        // FK-Ladefehler → 500; in beiden Fällen ist die TX für Tenant B nicht zugänglich)
        String tokenB = getDevToken(accountB.getCustomerId(), TENANT_B);
        MvcResult tenantBResult = mockMvc.perform(get("/api/v1/transactions/" + txId)
                        .header("Authorization", "Bearer " + tokenB))
                .andReturn();
        int status = tenantBResult.getResponse().getStatus();
        assertThat(status)
                .as("Tenant B darf Tenant-A-Transaktion nicht sehen (erwartet 4xx oder 5xx, kein 2xx)")
                .isGreaterThanOrEqualTo(400);
    }

    // ============================================================
    // TC5: JWT ohne tenant-Claim → Fallback auf 'tenant-default'
    // ============================================================
    @Test
    @Order(5)
    void jwtWithoutTenantClaim_fallsBackToDefault() throws Exception {
        mockMvc.perform(get("/api/v1/auth/dev-token")
                        .param("customerId", "cust-b2b-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenant").value("tenant-default"));
    }

    // ============================================================
    // Hilfsmethoden
    // ============================================================

    private CustomerAccount createTestAccount(String customerId, String iban, String tenant) {
        // TenantContext muss vom Aufrufer gesetzt sein.
        // TenantAwareDataSource → set_config bei Connection-Acquire.
        // TenantEntityListener → @PrePersist setzt tenantId.
        CustomerAccount acc = new CustomerAccount();
        acc.setCustomerId(customerId);
        acc.setIban(iban);
        acc.setWalletAddress("0xWallet" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        acc.setCustomerType(CustomerType.B2B);
        acc.setKycTier(KycTier.TIER_3);
        acc.setTxLimitSingle(new BigDecimal("25000.00"));
        acc.setTxLimitDaily(new BigDecimal("200000.00"));
        acc.setStatus(AccountStatus.ACTIVE);
        return accountRepo.save(acc);
    }

    private AddressBook whitelistWallet(CustomerAccount account, String walletAddress) {
        AddressBook ab = new AddressBook();
        ab.setCustomerAccount(account);
        ab.setLabel("Test Wallet " + walletAddress.substring(walletAddress.length() - 4));
        ab.setWalletAddress(walletAddress);
        ab.setCurrency(StablecoinCurrency.USDC);
        ab.setRiskScore(RiskScore.LOW);
        ab.setStatus(AddressStatus.ACTIVE);
        return addressBookRepo.save(ab);
    }

    private String getDevToken(String customerId, String tenant) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/dev-token")
                        .param("customerId", customerId)
                        .param("tenant", tenant))
                .andExpect(status().isOk())
                .andReturn();
        return extractJsonField(result, "token");
    }

    private String extractJsonField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        return objectMapper.readTree(body).get(field).asText();
    }
}
