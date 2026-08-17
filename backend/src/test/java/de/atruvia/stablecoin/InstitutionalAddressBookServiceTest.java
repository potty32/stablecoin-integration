package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.dto.request.b2b.AddInstitutionalAddressRequest;
import de.atruvia.stablecoin.dto.response.InstitutionalAddressBookResponse;
import de.atruvia.stablecoin.entity.InstitutionalAddressBook;
import de.atruvia.stablecoin.entity.InstitutionalAddressStatus;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.InstitutionalAddressBookRepository;
import de.atruvia.stablecoin.service.b2b.InstitutionalAddressBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InstitutionalAddressBookServiceTest {

    @Mock private InstitutionalAddressBookRepository repository;
    @Mock private ChainalysisClient chainalysisClient;
    @Mock private AuditLogRepository auditLogRepository;

    private InstitutionalAddressBookService service;

    private static final String USER_ID = "admin-inst-01";
    private static final String WALLET = "0xAbCdEf1234567890ABcDeF1234567890aBcDeF12";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new InstitutionalAddressBookService(repository, chainalysisClient, auditLogRepository);
    }

    private InstitutionalAddressBook savedEntry() {
        InstitutionalAddressBook entry = new InstitutionalAddressBook();
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        entry.setLabel("Institution Wallet");
        entry.setWalletAddress(WALLET);
        entry.setCurrency(StablecoinCurrency.USDC);
        entry.setRiskScore(RiskScore.LOW);
        entry.setStatus(InstitutionalAddressStatus.ACTIVE);
        entry.setCreatedBy(USER_ID);
        ReflectionTestUtils.setField(entry, "verifiedAt", LocalDateTime.now());
        return entry;
    }

    @Test
    @DisplayName("1. addAddress: approved -> entity saved with createdBy = userId, response has correct fields")
    void addAddress_approved_savedWithCreatedByAndResponseCorrect() {
        AddressScreenResponseDto screening = new AddressScreenResponseDto(WALLET, "LOW", List.of(), false, true);
        when(chainalysisClient.screenAddress(any(AddressScreenRequestDto.class))).thenReturn(screening);

        InstitutionalAddressBook saved = savedEntry();
        when(repository.save(any(InstitutionalAddressBook.class))).thenReturn(saved);

        AddInstitutionalAddressRequest request = new AddInstitutionalAddressRequest(
                "Institution Wallet", WALLET, StablecoinCurrency.USDC);

        InstitutionalAddressBookResponse response = service.addAddress(request, USER_ID);

        assertThat(response.walletAddress()).isEqualTo(WALLET);
        assertThat(response.createdBy()).isEqualTo(USER_ID);
        assertThat(response.riskScore()).isEqualTo(RiskScore.LOW);
        assertThat(response.status()).isEqualTo(InstitutionalAddressStatus.ACTIVE);

        verify(repository).save(any(InstitutionalAddressBook.class));
        verify(auditLogRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("2. addAddress: screening rejected -> ComplianceBlockException thrown")
    void addAddress_notApproved_throwsComplianceBlockException() {
        AddressScreenResponseDto screening = new AddressScreenResponseDto(
                WALLET, "HIGH", List.of("SANCTIONS"), false, false);
        when(chainalysisClient.screenAddress(any(AddressScreenRequestDto.class))).thenReturn(screening);

        AddInstitutionalAddressRequest request = new AddInstitutionalAddressRequest(
                "Bad Wallet", WALLET, StablecoinCurrency.USDC);

        assertThatThrownBy(() -> service.addAddress(request, USER_ID))
                .isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining(WALLET);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("3. addAddress fallback: ComplianceBlockException input -> rethrows same instance")
    void fallback_complianceBlockException_rethrowsSame() throws Exception {
        ComplianceBlockException original = new ComplianceBlockException(WALLET, "HIGH");
        AddInstitutionalAddressRequest request = new AddInstitutionalAddressRequest(
                "Blocked", WALLET, StablecoinCurrency.USDC);

        Method method = InstitutionalAddressBookService.class.getDeclaredMethod(
                "addAddressFallback", AddInstitutionalAddressRequest.class, String.class, Throwable.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(service, request, USER_ID, original);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isSameAs(original);
    }

    @Test
    @DisplayName("4. addAddress fallback: generic RuntimeException -> new ComplianceBlockException with UNAVAILABLE")
    void fallback_runtimeException_throwsComplianceBlockWithUnavailable() throws Exception {
        RuntimeException cause = new RuntimeException("circuit breaker open");
        AddInstitutionalAddressRequest request = new AddInstitutionalAddressRequest(
                "Test", WALLET, StablecoinCurrency.USDC);

        Method method = InstitutionalAddressBookService.class.getDeclaredMethod(
                "addAddressFallback", AddInstitutionalAddressRequest.class, String.class, Throwable.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(service, request, USER_ID, cause);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @Test
    @DisplayName("5. listAddresses -> returns all ACTIVE institutional entries")
    void listAddresses_returnsActiveEntries() {
        InstitutionalAddressBook entry = savedEntry();
        when(repository.findByStatus(InstitutionalAddressStatus.ACTIVE)).thenReturn(List.of(entry));

        List<InstitutionalAddressBookResponse> results = service.listAddresses();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).walletAddress()).isEqualTo(WALLET);
        assertThat(results.get(0).status()).isEqualTo(InstitutionalAddressStatus.ACTIVE);
    }

    @Test
    @DisplayName("6. revokeAddress: found -> status set to REVOKED; not found -> NoSuchElementException")
    void revokeAddress_foundSetsRevoked_notFoundThrows() {
        UUID addressId = UUID.randomUUID();
        InstitutionalAddressBook entry = new InstitutionalAddressBook();
        ReflectionTestUtils.setField(entry, "id", addressId);
        entry.setLabel("To Revoke");
        entry.setWalletAddress(WALLET);
        entry.setStatus(InstitutionalAddressStatus.ACTIVE);

        when(repository.findById(addressId)).thenReturn(Optional.of(entry));

        service.revokeAddress(addressId, USER_ID);

        assertThat(entry.getStatus()).isEqualTo(InstitutionalAddressStatus.REVOKED);
        verify(repository).save(entry);
        verify(auditLogRepository).save(any());

        // Not found case
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeAddress(missingId, USER_ID))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Institutional address not found");
    }
}
