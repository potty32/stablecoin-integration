package de.atruvia.stablecoin;

import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integrationstests für den Inbound-Stablecoin-Empfang.
 *
 * TC1: LOW_RISK-Sender → Webhook → SETTLED + Core-Banking-Gutschrift
 * TC2: HIGH_RISK-Sender (0xDEAD...) → Webhook → FAILED + AuditLog AML_INBOUND_BLOCK
 *
 * Verwendet AbstractLocalDbTest (stablecoin-User, BYPASSRLS) — kein JWT nötig
 * (Webhook-Endpunkt ist permitAll).
 */
@AutoConfigureMockMvc
class InboundProcessingTest extends AbstractLocalDbTest {

    // Seed-Wallet aus V1__init.sql (tenant-default)
    private static final String B2B_WALLET    = "0xBankB2BWallet000000000000000000000000001";
    private static final String LOW_RISK_SENDER = "0xA100000000000000000000000000000000000001";
    private static final String HIGH_RISK_SENDER = "0xDEAD000000000000000000000000000000000000";

    @Autowired MockMvc mockMvc;
    @Autowired StablecoinTransactionRepository txRepo;
    @Autowired AuditLogRepository auditLogRepo;
    @Autowired OutboxMessageRepository outboxRepo;
    @Autowired ApprovalWorkflowRepository approvalRepo;

    @AfterEach
    void cleanup() {
        outboxRepo.deleteAllInBatch();
        auditLogRepo.deleteAllInBatch();
        approvalRepo.deleteAllInBatch();
        txRepo.deleteAllInBatch();
    }

    // ── TC1: LOW_RISK → SETTLED ────────────────────────────────────────────

    @Test
    void lowRiskInbound_settlesWithCoreBankingCredit() throws Exception {
        String hash = "0xtest-low-risk-" + System.currentTimeMillis();

        MvcResult result = mockMvc.perform(post("/api/v1/b2b/inbound/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "walletId":      "%s",
                              "amount":        1000,
                              "currency":      "USDC",
                              "blockchainHash":"%s",
                              "senderWallet":  "%s"
                            }
                            """.formatted(B2B_WALLET, hash, LOW_RISK_SENDER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andReturn();

        // TX-Status in DB prüfen
        String txId = extractField(result, "transactionId");
        var tx = txRepo.findById(java.util.UUID.fromString(txId)).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.SETTLED);
        assertThat(tx.getBlockchainHash()).isEqualTo(hash);

        // AuditLog-Kette prüfen: INCOMING → COMPLIANCE_PENDING → COMPLIANCE_APPROVED → SETTLED
        var logs = auditLogRepo.findByTransactionIdOrderByTimestampAsc(tx.getId());
        var statuses = logs.stream()
                .filter(l -> l.getToStatus() != null)
                .map(l -> l.getToStatus().name())
                .toList();
        assertThat(statuses).containsSubsequence("INCOMING", "COMPLIANCE_PENDING", "COMPLIANCE_APPROVED", "SETTLED");

        // Kein AML_INBOUND_BLOCK-Eintrag
        boolean hasBlock = logs.stream().anyMatch(l -> "AML_INBOUND_BLOCK".equals(l.getAction()));
        assertThat(hasBlock).isFalse();
    }

    // ── TC2: HIGH_RISK → FAILED + AML_INBOUND_BLOCK ───────────────────────

    @Test
    void highRiskInbound_failsWithAmlBlock() throws Exception {
        String hash = "0xtest-high-risk-" + System.currentTimeMillis();

        mockMvc.perform(post("/api/v1/b2b/inbound/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "walletId":      "%s",
                              "amount":        500,
                              "currency":      "EURC",
                              "blockchainHash":"%s",
                              "senderWallet":  "%s"
                            }
                            """.formatted(B2B_WALLET, hash, HIGH_RISK_SENDER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        // TX in DB prüfen
        var tx = txRepo.findByBlockchainHash(hash).orElseThrow();
        assertThat(tx.getStatus()).isEqualTo(TransactionStatus.FAILED);

        // AuditLog enthält AML_INBOUND_BLOCK
        var logs = auditLogRepo.findByTransactionIdOrderByTimestampAsc(tx.getId());
        boolean hasBlock = logs.stream().anyMatch(l -> "AML_INBOUND_BLOCK".equals(l.getAction()));
        assertThat(hasBlock).isTrue();

        // Status-Kette: INCOMING → COMPLIANCE_PENDING → COMPLIANCE_REJECTED → FAILED
        var statuses = logs.stream()
                .filter(l -> l.getToStatus() != null)
                .map(l -> l.getToStatus().name())
                .toList();
        assertThat(statuses).containsSubsequence("INCOMING", "COMPLIANCE_PENDING", "COMPLIANCE_REJECTED", "FAILED");
    }

    private String extractField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        return node.get(field).asText();
    }
}
