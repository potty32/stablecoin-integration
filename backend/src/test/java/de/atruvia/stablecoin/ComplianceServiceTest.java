package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.entity.AuditLog;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.service.compliance.ComplianceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ComplianceServiceTest {

    @Mock private ChainalysisClient chainalysisClient;
    @Mock private AuditLogRepository auditLogRepository;

    private ComplianceService complianceService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        complianceService = new ComplianceService(chainalysisClient, auditLogRepository);
    }

    @Test
    @DisplayName("1. Approved address -> 1 audit log with action COMPLIANCE_SCREEN")
    void screenAndAssert_approved_savesOneAuditLogWithCorrectAction() {
        UUID txId = UUID.randomUUID();
        AddressScreenResponseDto response = new AddressScreenResponseDto(
                "0xabc", "LOW", List.of(), false, true);
        when(chainalysisClient.screenAddress(any(AddressScreenRequestDto.class))).thenReturn(response);

        complianceService.screenAndAssert("0xabc", txId, "user-1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getAction()).isEqualTo("COMPLIANCE_SCREEN");
    }

    @Test
    @DisplayName("2. Blocked address -> 2 audit logs (SCREEN + BLOCKED) + ComplianceBlockException thrown")
    void screenAndAssert_notApproved_savesTwoAuditLogsAndThrows() {
        UUID txId = UUID.randomUUID();
        AddressScreenResponseDto response = new AddressScreenResponseDto(
                "0xbad", "HIGH", List.of("SANCTIONS"), false, false);
        when(chainalysisClient.screenAddress(any(AddressScreenRequestDto.class))).thenReturn(response);

        assertThatThrownBy(() -> complianceService.screenAndAssert("0xbad", txId, "user-2"))
                .isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining("0xbad");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(2)).save(captor.capture());
        List<AuditLog> saved = captor.getAllValues();
        assertThat(saved.get(0).getAction()).isEqualTo("COMPLIANCE_SCREEN");
        assertThat(saved.get(1).getAction()).isEqualTo("COMPLIANCE_BLOCKED");
    }

    @Test
    @DisplayName("3. Chainalysis throws RuntimeException -> propagates, 0 audit log saves")
    void screenAndAssert_chainalysisThrows_propagatesExceptionZeroSaves() {
        UUID txId = UUID.randomUUID();
        when(chainalysisClient.screenAddress(any())).thenThrow(new RuntimeException("connection timeout"));

        assertThatThrownBy(() -> complianceService.screenAndAssert("0xabc", txId, "user-3"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("connection timeout");

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("4. Fallback with ComplianceBlockException input -> rethrows same exception")
    void fallback_complianceBlockException_rethrowsSame() throws Exception {
        ComplianceBlockException original = new ComplianceBlockException("0xbad", "HIGH");
        Method method = ComplianceService.class.getDeclaredMethod(
                "screenAddressFallback", String.class, UUID.class, String.class, Throwable.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(complianceService, "0xbad", UUID.randomUUID(), "user-4", original);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isSameAs(original);
    }

    @Test
    @DisplayName("4b. Fallback with generic RuntimeException -> new ComplianceBlockException with UNAVAILABLE")
    void fallback_runtimeException_throwsComplianceBlockWithUnavailable() throws Exception {
        RuntimeException runtimeEx = new RuntimeException("circuit breaker open");
        Method method = ComplianceService.class.getDeclaredMethod(
                "screenAddressFallback", String.class, UUID.class, String.class, Throwable.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(complianceService, "0xabc", UUID.randomUUID(), "user-5", runtimeEx);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining("UNAVAILABLE");
    }
}
