package de.atruvia.stablecoin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CommonControllerOwnershipTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    private static final String B2B_IBAN  = "DE89370400440532013000";
    private static final String B2C_IBAN  = "DE27200400600532013001";

    // TC5a: Eigene IBAN → 200
    @Test
    @WithMockUser(username = "cust-b2b-001")
    void ownAccount_returns200() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{iban}/balance", B2B_IBAN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iban").value(B2B_IBAN));
    }

    // TC5b: Fremde IBAN → 403 AUTH_001
    @Test
    @WithMockUser(username = "cust-b2b-001")
    void foreignAccount_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{iban}/balance", B2C_IBAN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_001"));
    }
}
