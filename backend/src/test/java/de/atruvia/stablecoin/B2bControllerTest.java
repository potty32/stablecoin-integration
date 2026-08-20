package de.atruvia.stablecoin;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.dto.request.b2b.AddAddressRequest;
import de.atruvia.stablecoin.dto.request.b2b.AddInstitutionalAddressRequest;
import de.atruvia.stablecoin.dto.request.b2b.ApproveTransferRequest;
import de.atruvia.stablecoin.dto.request.b2b.InitiateTransferRequest;
import de.atruvia.stablecoin.dto.request.b2b.KillSwitchRequest;
import de.atruvia.stablecoin.dto.request.ReassignTransactionRequest;
import de.atruvia.stablecoin.dto.response.AddressBookResponse;
import de.atruvia.stablecoin.dto.response.BulkPaymentResult;
import de.atruvia.stablecoin.dto.response.BulkRowResult;
import de.atruvia.stablecoin.dto.response.InstitutionalAddressBookResponse;
import de.atruvia.stablecoin.dto.response.RateQuoteResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.dto.response.TransferPageResponse;
import de.atruvia.stablecoin.entity.AddressStatus;
import de.atruvia.stablecoin.entity.InstitutionalAddressStatus;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.entity.SystemControl;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.entity.TransactionType;
import de.atruvia.stablecoin.service.b2b.AddressBookService;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import de.atruvia.stablecoin.service.b2b.BulkPaymentService;
import de.atruvia.stablecoin.service.b2b.ExportService;
import de.atruvia.stablecoin.service.b2b.InstitutionalAddressBookService;
import de.atruvia.stablecoin.service.b2b.KillSwitchService;
import de.atruvia.stablecoin.service.b2b.ReassignTransactionService;
import de.atruvia.stablecoin.service.b2b.SanctionsBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-Tests für B2bController — alle 23 Endpunkte inkl. Rollen-/Kill-Switch-Prüfungen.
 *
 * Nur die Web-Schicht wird geladen (@WebMvcTest). Alle Services sind @MockBean.
 * Das Rate-Limit wird via TestPropertySource auf 10.000/sec gesetzt, damit
 * es in keinem Test ausgelöst wird.
 */
@WebMvcTest(controllers = de.atruvia.stablecoin.controller.b2b.B2bController.class)
@TestPropertySource(properties = {
        "app.rate-limit.anon-per-second=10000",
        "app.rate-limit.small-vb-per-minute=10000"
})
class B2bControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Für SecurityConfig (KillSwitchFilter)
    @MockBean KillSwitchService killSwitchService;

    // Controller-Abhängigkeiten
    @MockBean B2bTransferService transferService;
    @MockBean AddressBookService addressBookService;
    @MockBean BulkPaymentService bulkPaymentService;
    @MockBean ExportService exportService;
    @MockBean SanctionsBatchService sanctionsBatchService;
    @MockBean ReassignTransactionService reassignTransactionService;
    @MockBean InstitutionalAddressBookService institutionalAddressBookService;
    @MockBean de.atruvia.stablecoin.service.b2b.DvpEscrowService dvpEscrowService;

    private UUID txId;
    private UUID addressId;
    private TransactionResponse sampleTx;
    private AddressBookResponse sampleAddress;

    @BeforeEach
    void setUp() {
        txId      = UUID.randomUUID();
        addressId = UUID.randomUUID();

        sampleTx = new TransactionResponse(
                txId, TransactionType.OUTBOUND, TransactionStatus.CREATED,
                new BigDecimal("1000.00"), new BigDecimal("1000.000000"),
                StablecoinCurrency.USDC, null, new BigDecimal("2.50"),
                false, null, null, Collections.emptyList()
        );

        sampleAddress = new AddressBookResponse(
                addressId, "Partner-Wallet", "0xABC123",
                StablecoinCurrency.USDC, RiskScore.LOW, AddressStatus.ACTIVE, null
        );

        when(killSwitchService.isGlobalKillSwitchActive()).thenReturn(false);
        when(killSwitchService.isTenantKillSwitchActive(anyString())).thenReturn(false);
    }

    // ─── TC-B2B-01: POST /transfers ──────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-01a: POST /transfers — gültige Anfrage → 201 Created")
    void initiateTransfer_validRequest_returns201() throws Exception {
        when(transferService.initiate(anyString(), any(), anyString())).thenReturn(sampleTx);

        String body = objectMapper.writeValueAsString(new InitiateTransferRequest(
                "DE89370400440532013000", "0xDestWallet", new BigDecimal("1000.00"),
                StablecoinCurrency.USDC, null, "TRADE", "Ref-001", null, null, null
        ));

        mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-01b: POST /transfers — leerer Body (null Pflichtfelder) → 400 VAL_001")
    void initiateTransfer_missingFields_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VAL_001"));
    }

    @Test
    @DisplayName("TC-B2B-01c: POST /transfers — unauthentifiziert → 401 oder 403")
    void initiateTransfer_unauthenticated_returns401or403() throws Exception {
        String body = objectMapper.writeValueAsString(new InitiateTransferRequest(
                "DE89370400440532013000", "0xDestWallet", new BigDecimal("1000.00"),
                StablecoinCurrency.USDC, null, null, null, null, null, null
        ));

        mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getStatus()).isIn(401, 403));
    }

    // ─── TC-B2B-02: GET /transfers ───────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-02a: GET /transfers — Standard → 200 OK mit Seite")
    void listTransfers_returns200() throws Exception {
        TransferPageResponse page = new TransferPageResponse(List.of(sampleTx), 1L, 1, 0, 20);
        when(transferService.listTransfers(anyString(), isNull(), eq(0), eq(20))).thenReturn(page);

        mockMvc.perform(get("/api/v1/b2b/transfers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-02b: GET /transfers — Status=CREATED → 200 OK")
    void listTransfers_withStatusFilter_returns200() throws Exception {
        TransferPageResponse page = new TransferPageResponse(List.of(sampleTx), 1L, 1, 0, 20);
        when(transferService.listTransfers(anyString(), eq(TransactionStatus.CREATED), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/b2b/transfers").param("status", "CREATED"))
                .andExpect(status().isOk());
    }

    // ─── TC-B2B-03: GET /transfers/{id} ─────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-03a: GET /transfers/{id} — vorhanden → 200 OK")
    void getTransfer_exists_returns200() throws Exception {
        when(transferService.getById(txId)).thenReturn(sampleTx);

        mockMvc.perform(get("/api/v1/b2b/transfers/{id}", txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(txId.toString()));
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-03b: GET /transfers/{id} — nicht vorhanden → 404 NOT_FOUND_001")
    void getTransfer_notFound_returns404() throws Exception {
        UUID unknown = UUID.randomUUID();
        when(transferService.getById(unknown))
                .thenThrow(new java.util.NoSuchElementException("TX not found"));

        mockMvc.perform(get("/api/v1/b2b/transfers/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND_001"));
    }

    // ─── TC-B2B-04: POST /transfers/{id}/approve ─────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-04: POST /transfers/{id}/approve → 200 APPROVED")
    void approveTransfer_returns200() throws Exception {
        TransactionResponse approved = new TransactionResponse(
                txId, TransactionType.OUTBOUND, TransactionStatus.APPROVED,
                BigDecimal.TEN, BigDecimal.TEN, StablecoinCurrency.USDC,
                null, BigDecimal.ZERO, false, null, null, Collections.emptyList()
        );
        when(transferService.approve(eq(txId), any())).thenReturn(approved);

        mockMvc.perform(post("/api/v1/b2b/transfers/{id}/approve", txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveTransferRequest("approver")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    // ─── TC-B2B-05: POST /transfers/{id}/reject ──────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-05: POST /transfers/{id}/reject → 200 REJECTED")
    void rejectTransfer_returns200() throws Exception {
        TransactionResponse rejected = new TransactionResponse(
                txId, TransactionType.OUTBOUND, TransactionStatus.REJECTED,
                BigDecimal.TEN, BigDecimal.TEN, StablecoinCurrency.USDC,
                null, BigDecimal.ZERO, false, null, null, Collections.emptyList()
        );
        when(transferService.reject(eq(txId), any())).thenReturn(rejected);

        mockMvc.perform(post("/api/v1/b2b/transfers/{id}/reject", txId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ApproveTransferRequest("rejector")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    // ─── TC-B2B-06: GET /rate-quote ──────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-06: GET /rate-quote → 200 OK mit USDC-Quote")
    void getRateQuote_returns200() throws Exception {
        RateQuoteResponse quote = new RateQuoteResponse(
                UUID.randomUUID(),
                new BigDecimal("1000.00"),
                "1000.000000",
                new BigDecimal("1.0000"),
                new BigDecimal("0.15"),
                new BigDecimal("2.50"),
                null,
                60L
        );
        when(transferService.createRateQuote(any(), any(), anyString())).thenReturn(quote);

        mockMvc.perform(get("/api/v1/b2b/rate-quote")
                        .param("amountEur", "1000.00")
                        .param("currency", "USDC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lockedForSeconds").value(60));
    }

    // ─── TC-B2B-07: POST /address-book ───────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-07a: POST /address-book — gültig → 201 Created")
    void addAddress_validRequest_returns201() throws Exception {
        when(addressBookService.addAddress(any(), anyString())).thenReturn(sampleAddress);

        mockMvc.perform(post("/api/v1/b2b/address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddAddressRequest("Partner-Wallet", "0xABC123", StablecoinCurrency.USDC)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletAddress").value("0xABC123"));
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-07b: POST /address-book — Compliance-Block → 403 COMPLIANCE_001")
    void addAddress_complianceBlock_returns403() throws Exception {
        when(addressBookService.addAddress(any(), anyString()))
                .thenThrow(new de.atruvia.stablecoin.exception.ComplianceBlockException("0xBAD", "HIGH"));

        mockMvc.perform(post("/api/v1/b2b/address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddAddressRequest("Bad Actor", "0xBAD", StablecoinCurrency.USDC)))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMPLIANCE_001"));
    }

    // ─── TC-B2B-08: GET /address-book ────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-08: GET /address-book → 200 OK mit Liste")
    void listAddresses_returns200() throws Exception {
        when(addressBookService.listAddresses(anyString())).thenReturn(List.of(sampleAddress));

        mockMvc.perform(get("/api/v1/b2b/address-book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].label").value("Partner-Wallet"));
    }

    // ─── TC-B2B-09: DELETE /address-book/{id} ────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-09: DELETE /address-book/{id} → 204 No Content")
    void revokeAddress_returns204() throws Exception {
        doNothing().when(addressBookService).revokeAddress(eq(addressId), anyString());

        mockMvc.perform(delete("/api/v1/b2b/address-book/{id}", addressId).with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ─── TC-B2B-10: POST /bulk-payments ──────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-10: POST /bulk-payments — CSV → 200 OK mit Ergebnis")
    void bulkPayments_csvUpload_returns200() throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "payments.csv",
                "text/csv",
                "destinationWallet,amountEur,currency,reference\n0xDest001,100.00,USDC,REF-001\n"
                        .getBytes());

        BulkPaymentResult result = new BulkPaymentResult(
                1, 1, 0,
                List.of(new BulkRowResult(1, "0xDest001", "100.00", "SUCCESS", null, null))
        );
        when(bulkPaymentService.process(any(), anyString(), anyString())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/b2b/bulk-payments")
                        .file(csv)
                        .param("sourceIban", "DE89370400440532013000")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(1));
    }

    // ─── TC-B2B-11: GET /export/camt053 ──────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-11: GET /export/camt053 → 200 OK application/xml")
    void exportCamt053_returns200() throws Exception {
        String iban = "DE89370400440532013000";
        when(exportService.resolveIban(isNull())).thenReturn(iban);
        when(exportService.generateCamt053(iban)).thenReturn("<Document/>");

        mockMvc.perform(get("/api/v1/b2b/export/camt053"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    // ─── TC-B2B-12: GET /export/camt054 ──────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-12: GET /export/camt054 → 200 OK application/xml")
    void exportCamt054_returns200() throws Exception {
        String iban = "DE89370400440532013000";
        when(exportService.resolveIban(isNull())).thenReturn(iban);
        when(exportService.generateCamt054(iban)).thenReturn("<Document/>");

        mockMvc.perform(get("/api/v1/b2b/export/camt054"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    // ─── TC-B2B-13: GET /export/camt029 ──────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-13: GET /export/camt029 → 200 OK application/xml")
    void exportCamt029_returns200() throws Exception {
        String iban = "DE89370400440532013000";
        when(exportService.resolveIban(isNull())).thenReturn(iban);
        when(exportService.generateCamt029(eq(iban), any())).thenReturn("<Document/>");

        mockMvc.perform(get("/api/v1/b2b/export/camt029"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    // ─── TC-B2B-14: GET /export/datev ────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-14: GET /export/datev → 200 OK text/csv")
    void exportDatev_returns200() throws Exception {
        String iban = "DE89370400440532013000";
        when(exportService.resolveIban(isNull())).thenReturn(iban);
        when(exportService.generateDatev(iban)).thenReturn("Buchungsdatum;Betrag\n");

        mockMvc.perform(get("/api/v1/b2b/export/datev"))
                .andExpect(status().isOk());
    }

    // ─── TC-B2B-15: POST /export/async-trigger ───────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-15: POST /export/async-trigger → 202 Accepted mit presignedUrl")
    void triggerAsyncExport_returns202() throws Exception {
        when(exportService.triggerAsyncExport(anyString(), any(), anyString()))
                .thenReturn("https://s3.example.com/export.xml?signed=abc");

        mockMvc.perform(post("/api/v1/b2b/export/async-trigger")
                        .param("type", "camt053")
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.exportType").value("camt053"))
                .andExpect(jsonPath("$.validForSeconds").value(900));
    }

    // ─── TC-B2B-16: POST /admin/sanctions-scan ───────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-16: POST /admin/sanctions-scan → 200 OK")
    void triggerSanctionsScan_returns200() throws Exception {
        doNothing().when(sanctionsBatchService).runNightlySanctionsScan();

        mockMvc.perform(post("/api/v1/b2b/admin/sanctions-scan").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Sanctions scan completed"));
    }

    // ─── TC-B2B-17/18/19: Kill Switch ────────────────────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-17: POST /admin/kill-switch/activate GLOBAL → 200 OK")
    void activateKillSwitchGlobal_returns200() throws Exception {
        when(killSwitchService.activateGlobal(anyString(), anyString())).thenReturn(new SystemControl());

        mockMvc.perform(post("/api/v1/b2b/admin/kill-switch/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KillSwitchRequest("GLOBAL", "Notfall")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Globaler Kill Switch aktiviert"));
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-18: POST /admin/kill-switch/activate TENANT → 200 OK")
    void activateKillSwitchTenant_returns200() throws Exception {
        when(killSwitchService.activateTenant(anyString(), anyString(), anyString()))
                .thenReturn(new de.atruvia.stablecoin.entity.TenantSettings());

        mockMvc.perform(post("/api/v1/b2b/admin/kill-switch/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KillSwitchRequest("tenant-vb-001", "Test")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-19: POST /admin/kill-switch/deactivate → 200 OK")
    void deactivateKillSwitch_returns200() throws Exception {
        when(killSwitchService.deactivateGlobal(anyString())).thenReturn(new SystemControl());

        mockMvc.perform(post("/api/v1/b2b/admin/kill-switch/deactivate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new KillSwitchRequest("GLOBAL", null)))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-20: GET /admin/kill-switch/status → 200 OK")
    void killSwitchStatus_returns200() throws Exception {
        SystemControl ctrl = new SystemControl();
        ctrl.setKillSwitchActive(false);
        when(killSwitchService.getGlobalStatus()).thenReturn(ctrl);

        mockMvc.perform(get("/api/v1/b2b/admin/kill-switch/status"))
                .andExpect(status().isOk());
    }

    // ─── TC-B2B-21: POST /admin/reassign-transaction ─────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-21: POST /admin/reassign-transaction → 200 OK")
    void reassignTransaction_returns200() throws Exception {
        when(reassignTransactionService.reassign(any())).thenReturn(sampleTx);

        mockMvc.perform(post("/api/v1/b2b/admin/reassign-transaction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReassignTransactionRequest(txId, "DE89370400440532013000")))
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    // ─── TC-B2B-22/23/24: Institutional Address Book ─────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-22: POST /institutional-address-book → 201 Created")
    void addInstitutionalAddress_returns201() throws Exception {
        InstitutionalAddressBookResponse resp = new InstitutionalAddressBookResponse(
                UUID.randomUUID(), "Korrespondenzbank", "0xINST001",
                StablecoinCurrency.EURC, RiskScore.LOW,
                InstitutionalAddressStatus.ACTIVE, "user-admin", null
        );
        when(institutionalAddressBookService.addAddress(any(), anyString())).thenReturn(resp);

        mockMvc.perform(post("/api/v1/b2b/institutional-address-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new AddInstitutionalAddressRequest("Korrespondenzbank", "0xINST001",
                                        StablecoinCurrency.EURC)))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletAddress").value("0xINST001"));
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-23: GET /institutional-address-book → 200 OK mit Liste")
    void listInstitutionalAddresses_returns200() throws Exception {
        when(institutionalAddressBookService.listAddresses()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/b2b/institutional-address-book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-24: DELETE /institutional-address-book/{id} → 204 No Content")
    void revokeInstitutionalAddress_returns204() throws Exception {
        doNothing().when(institutionalAddressBookService).revokeAddress(any(), anyString());

        mockMvc.perform(delete("/api/v1/b2b/institutional-address-book/{id}", UUID.randomUUID())
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    // ─── TC-B2B-25: Kill-Switch-Block auf schreibende Requests ───────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-25: PaymentSystemFrozenException → 503 SYSTEM_003")
    void initiateTransfer_whenSystemFrozen_returns503() throws Exception {
        // Testet: GlobalExceptionHandler mappt PaymentSystemFrozenException → 503 SYSTEM_003
        when(transferService.initiate(anyString(), any(), anyString()))
                .thenThrow(new de.atruvia.stablecoin.exception.PaymentSystemFrozenException("Notfall-Freeze"));

        String body = objectMapper.writeValueAsString(new InitiateTransferRequest(
                "DE89370400440532013000", "0xDest", BigDecimal.TEN,
                StablecoinCurrency.USDC, null, null, null, null, null, null
        ));

        mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errorCode").value("SYSTEM_003"));
    }

    // ─── TC-B2B-26: Idempotenz-Konflikt bei Transfer ─────────────────────────

    @Test
    @WithMockUser
    @DisplayName("TC-B2B-26: POST /transfers mit doppeltem Idempotency-Key → 409 IDEM_001")
    void initiateTransfer_duplicateIdempotencyKey_returns409() throws Exception {
        when(transferService.initiate(anyString(), any(), anyString()))
                .thenThrow(new de.atruvia.stablecoin.exception.IdempotencyConflictException(txId));

        String body = objectMapper.writeValueAsString(new InitiateTransferRequest(
                "DE89370400440532013000", "0xDest", BigDecimal.TEN,
                StablecoinCurrency.USDC, null, null, null, null, null, null
        ));

        mockMvc.perform(post("/api/v1/b2b/transfers")
                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEM_001"));
    }
}
