package de.atruvia.stablecoin;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atruvia.stablecoin.dto.request.DevChatRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-Tests für den DevChatController und die DevChatKnowledgeService-KB.
 *
 * Prüft: korrekte Tenant-Propagation, Keyword-Zuordnung, Fallback-Antwort,
 * Mandantenpersonalisierung und HTTP-Semantik.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@DisplayName("DevChatController — Copilot Knowledge Base")
class DevChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── TC-01: RLS-Frage → korrekte Wissensdatenbank-Antwort ──────────────────

    @Test
    @DisplayName("TC-01: Frage zu RLS liefert PostgreSQL-Isolations-Erklärung")
    void tc01_rlsQuery_returnsDetailedTenantIsolationAnswer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/common/dev-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DevChatRequest("Wie funktioniert die Row Level Security?",
                                        "tenant-kleine-vb"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.sourceReferences").isArray())
                .andReturn();

        String reply = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reply").asText();

        assertThat(reply).containsIgnoringCase("stablecoin_app");
        assertThat(reply).containsIgnoringCase("current_setting");
        assertThat(reply).containsIgnoringCase("tenant");
    }

    // ── TC-02: Tenant-Propagation personaliert Antwort ────────────────────────

    @Test
    @DisplayName("TC-02: Tenant-ID wird in Antwort eingebettet (Personalisierung)")
    void tc02_tenantId_isEmbeddedInReply() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/common/dev-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DevChatRequest("Wie hoch sind unsere Transaktionslimits?",
                                        "tenant-kleine-vb"))))
                .andExpect(status().isOk())
                .andReturn();

        String reply = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reply").asText();

        // Tenant-Name muss in personalisierter Antwort erscheinen
        assertThat(reply).containsAnyOf("tenant-kleine-vb", "Volksbank Kleinstadt", "Volksbank");
    }

    // ── TC-03: AML/Sanktions-Frage → GwG-Antwort ─────────────────────────────

    @Test
    @DisplayName("TC-03: Sanktions-Frage liefert GwG §43 und Chainalysis-Erklärung")
    void tc03_sanctionQuery_returnsAmlGwgAnswer() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/common/dev-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DevChatRequest(
                                        "Was passiert wenn wir Geldwäsche-Verdacht feststellen?",
                                        "tenant-default"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceReferences").isArray())
                .andReturn();

        String reply = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reply").asText();
        String sources = result.getResponse().getContentAsString();

        assertThat(reply).containsIgnoringCase("chainalysis");
        assertThat(reply).containsIgnoringCase("compliance");
        assertThat(sources).contains("GwG");
    }

    // ── TC-04: Outbox-Frage → At-Least-Once-Erklärung ────────────────────────

    @Test
    @DisplayName("TC-04: Outbox-Frage liefert Crash-Recovery-Erklärung")
    void tc04_outboxQuery_returnsCrashRecoveryExplanation() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/common/dev-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DevChatRequest(
                                        "Wie verhindert das Outbox Pattern Datenverlust beim Absturz?",
                                        "tenant-grosse-vb"))))
                .andExpect(status().isOk())
                .andReturn();

        String reply = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reply").asText();

        assertThat(reply).containsIgnoringCase("outbox");
        assertThat(reply).containsAnyOf("At-Least-Once", "PENDING", "Recovery", "5 Sekunden");
    }

    // ── TC-05: Unbekannte Frage → sinnvoller Fallback ────────────────────────

    @Test
    @DisplayName("TC-05: Unbekanntes Thema liefert hilfreichen Fallback mit Themenliste")
    void tc05_unknownTopic_returnsFallbackWithTopicList() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/common/dev-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DevChatRequest(
                                        "Wie ist das Wetter in Frankfurt heute?",
                                        "tenant-default"))))
                .andExpect(status().isOk())
                .andReturn();

        String reply = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("reply").asText();

        // Fallback nennt verfügbare Themenbereiche
        assertThat(reply).containsAnyOf("Multi-Tenancy", "Outbox", "Regulatorik", "Yield", "Security");
    }

    // ── TC-06: Leere Nachricht → 400 Bad Request ──────────────────────────────

    @Test
    @DisplayName("TC-06: Leere Nachricht wird mit HTTP 400 abgelehnt")
    void tc06_emptyMessage_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/common/dev-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DevChatRequest("", "tenant-default"))))
                .andExpect(status().isBadRequest());
    }
}
