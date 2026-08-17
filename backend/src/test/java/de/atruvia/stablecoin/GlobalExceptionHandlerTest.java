package de.atruvia.stablecoin;

import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.exception.GlobalExceptionHandler;
import de.atruvia.stablecoin.exception.IdempotencyConflictException;
import de.atruvia.stablecoin.exception.TaurusLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        static RuntimeException exceptionToThrow = new RuntimeException("default");

        @GetMapping("/throw")
        public void throwException() {
            throw exceptionToThrow;
        }
    }

    private MockMvc mvc;
    private static final ThrowingController controller = new ThrowingController();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ResultActions perform(RuntimeException ex) throws Exception {
        ThrowingController.exceptionToThrow = ex;
        return mvc.perform(get("/throw"));
    }

    @Test
    @DisplayName("AccessDeniedException -> 403 FORBIDDEN + errorCode AUTH_001")
    void accessDenied_returns403AuthCode() throws Exception {
        perform(new AccessDeniedException("access denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("AUTH_001"));
    }

    @Test
    @DisplayName("ComplianceBlockException -> 403 FORBIDDEN + errorCode COMPLIANCE_001")
    void complianceBlock_returns403ComplianceCode() throws Exception {
        perform(new ComplianceBlockException("0xbadaddr", "HIGH"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("COMPLIANCE_001"));
    }

    @Test
    @DisplayName("TaurusLimitExceededException -> 403 FORBIDDEN + errorCode TAURUS_001")
    void taurusLimit_returns403TaurusCode() throws Exception {
        perform(new TaurusLimitExceededException("limit exceeded"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("TAURUS_001"));
    }

    @Test
    @DisplayName("IdempotencyConflictException -> 409 CONFLICT + errorCode IDEM_001")
    void idempotencyConflict_returns409IdemCode() throws Exception {
        perform(new IdempotencyConflictException(UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEM_001"));
    }

    @Test
    @DisplayName("NoSuchElementException -> 404 NOT_FOUND + errorCode NOT_FOUND_001")
    void notFound_returns404NotFoundCode() throws Exception {
        perform(new NoSuchElementException("entity not found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("NOT_FOUND_001"));
    }

    @Test
    @DisplayName("IllegalStateException -> 400 BAD_REQUEST + errorCode BIZ_001")
    void illegalState_returns400BizCode() throws Exception {
        perform(new IllegalStateException("invalid state transition"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BIZ_001"));
    }

    @Test
    @DisplayName("IllegalArgumentException -> 400 BAD_REQUEST + errorCode VAL_002")
    void illegalArgument_returns400ValCode() throws Exception {
        perform(new IllegalArgumentException("invalid argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VAL_002"));
    }

    @Test
    @DisplayName("DataIntegrityViolationException -> 409 CONFLICT + errorCode CONFLICT_001")
    void dataIntegrityViolation_returns409ConflictCode() throws Exception {
        perform(new DataIntegrityViolationException("duplicate key value"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("CONFLICT_001"));
    }

    @Test
    @DisplayName("Generic RuntimeException -> 500 INTERNAL_SERVER_ERROR + errorCode SYS_001")
    void genericRuntimeException_returns500SysCode() throws Exception {
        perform(new RuntimeException("something went wrong"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SYS_001"));
    }

    @Test
    @DisplayName("NullPointerException -> 500 INTERNAL_SERVER_ERROR + errorCode SYS_001")
    void nullPointerException_returns500SysCode() throws Exception {
        perform(new NullPointerException("null ref"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("SYS_001"));
    }
}
