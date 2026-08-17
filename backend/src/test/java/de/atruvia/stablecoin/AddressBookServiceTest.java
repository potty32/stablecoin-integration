package de.atruvia.stablecoin;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import de.atruvia.stablecoin.dto.request.b2b.AddAddressRequest;
import de.atruvia.stablecoin.dto.response.AddressBookResponse;
import de.atruvia.stablecoin.entity.AddressBook;
import de.atruvia.stablecoin.entity.AddressStatus;
import de.atruvia.stablecoin.entity.CustomerAccount;
import de.atruvia.stablecoin.entity.RiskScore;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.exception.ComplianceBlockException;
import de.atruvia.stablecoin.repository.AddressBookRepository;
import de.atruvia.stablecoin.repository.AuditLogRepository;
import de.atruvia.stablecoin.repository.CustomerAccountRepository;
import de.atruvia.stablecoin.service.b2b.AddressBookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AddressBookServiceTest {

    @Mock private AddressBookRepository addressBookRepository;
    @Mock private CustomerAccountRepository accountRepository;
    @Mock private ChainalysisClient chainalysisClient;
    @Mock private AuditLogRepository auditLogRepository;

    private AddressBookService service;

    private static final String USER_ID = "user-addr-01";
    private static final String WALLET = "0xAbCdEf1234567890ABcDeF1234567890aBcDeF12";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new AddressBookService(
                addressBookRepository, accountRepository, chainalysisClient, auditLogRepository);
    }

    private CustomerAccount mockAccount() {
        CustomerAccount acc = new CustomerAccount();
        ReflectionTestUtils.setField(acc, "id", UUID.randomUUID());
        acc.setCustomerId(USER_ID);
        return acc;
    }

    private AddressBook savedAddressBook(CustomerAccount acc) {
        AddressBook entry = new AddressBook();
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        entry.setCustomerAccount(acc);
        entry.setLabel("My Wallet");
        entry.setWalletAddress(WALLET);
        entry.setCurrency(StablecoinCurrency.USDC);
        entry.setRiskScore(RiskScore.LOW);
        entry.setStatus(AddressStatus.ACTIVE);
        entry.setVerifiedAt(LocalDateTime.now());
        return entry;
    }

    @Test
    @DisplayName("1. addAddress: screening approved -> entry saved, response returned")
    void addAddress_approved_returnsSavedEntry() {
        CustomerAccount acc = mockAccount();
        when(accountRepository.findByCustomerId(USER_ID)).thenReturn(Optional.of(acc));

        AddressScreenResponseDto screening = new AddressScreenResponseDto(WALLET, "LOW", List.of(), false, true);
        when(chainalysisClient.screenAddress(any(AddressScreenRequestDto.class))).thenReturn(screening);

        AddressBook saved = savedAddressBook(acc);
        when(addressBookRepository.save(any(AddressBook.class))).thenReturn(saved);

        AddAddressRequest request = new AddAddressRequest("My Wallet", WALLET, StablecoinCurrency.USDC);
        AddressBookResponse response = service.addAddress(request, USER_ID);

        assertThat(response.walletAddress()).isEqualTo(WALLET);
        assertThat(response.riskScore()).isEqualTo(RiskScore.LOW);
        assertThat(response.status()).isEqualTo(AddressStatus.ACTIVE);
        verify(addressBookRepository).save(any(AddressBook.class));
        verify(auditLogRepository, atLeastOnce()).save(any());
    }

    @Test
    @DisplayName("2. addAddress: screening not approved -> ComplianceBlockException thrown")
    void addAddress_notApproved_throwsComplianceBlockException() {
        CustomerAccount acc = mockAccount();
        when(accountRepository.findByCustomerId(USER_ID)).thenReturn(Optional.of(acc));

        AddressScreenResponseDto screening = new AddressScreenResponseDto(WALLET, "HIGH", List.of("SANCTIONS"), false, false);
        when(chainalysisClient.screenAddress(any(AddressScreenRequestDto.class))).thenReturn(screening);

        AddAddressRequest request = new AddAddressRequest("Bad Wallet", WALLET, StablecoinCurrency.USDC);

        assertThatThrownBy(() -> service.addAddress(request, USER_ID))
                .isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining(WALLET);

        verify(addressBookRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. addAddress fallback: ComplianceBlockException input -> rethrows same")
    void addAddressFallback_complianceBlockException_rethrows() throws Exception {
        ComplianceBlockException original = new ComplianceBlockException(WALLET, "HIGH");
        AddAddressRequest request = new AddAddressRequest("Blocked", WALLET, StablecoinCurrency.USDC);

        var method = AddressBookService.class.getDeclaredMethod(
                "addAddressFallback", AddAddressRequest.class, String.class, Throwable.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(service, request, USER_ID, original);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }).isSameAs(original);
    }

    @Test
    @DisplayName("4. addAddress fallback: RuntimeException -> new ComplianceBlockException with UNAVAILABLE")
    void addAddressFallback_runtimeException_throwsComplianceBlockUnavailable() throws Exception {
        RuntimeException cause = new RuntimeException("circuit open");
        AddAddressRequest request = new AddAddressRequest("Test", WALLET, StablecoinCurrency.USDC);

        var method = AddressBookService.class.getDeclaredMethod(
                "addAddressFallback", AddAddressRequest.class, String.class, Throwable.class);
        method.setAccessible(true);

        assertThatThrownBy(() -> {
            try {
                method.invoke(service, request, USER_ID, cause);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(ComplianceBlockException.class)
                .hasMessageContaining("UNAVAILABLE");
    }

    @Test
    @DisplayName("5. listAddresses: account found -> returns ACTIVE addresses")
    void listAddresses_accountFound_returnsActiveAddresses() {
        CustomerAccount acc = mockAccount();
        when(accountRepository.findByCustomerId(USER_ID)).thenReturn(Optional.of(acc));

        AddressBook entry = new AddressBook();
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        entry.setLabel("My Wallet");
        entry.setWalletAddress(WALLET);
        entry.setCurrency(StablecoinCurrency.USDC);
        entry.setRiskScore(RiskScore.LOW);
        entry.setStatus(AddressStatus.ACTIVE);
        entry.setVerifiedAt(LocalDateTime.now());

        when(addressBookRepository.findByCustomerAccountIdAndStatus(acc.getId(), AddressStatus.ACTIVE))
                .thenReturn(List.of(entry));

        List<AddressBookResponse> results = service.listAddresses(USER_ID);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).walletAddress()).isEqualTo(WALLET);
        assertThat(results.get(0).status()).isEqualTo(AddressStatus.ACTIVE);
    }

    @Test
    @DisplayName("6. listAddresses: no account found -> returns empty list")
    void listAddresses_noAccount_returnsEmptyList() {
        when(accountRepository.findByCustomerId(USER_ID)).thenReturn(Optional.empty());

        List<AddressBookResponse> results = service.listAddresses(USER_ID);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("7. revokeAddress: found -> status set to REVOKED and saved")
    void revokeAddress_found_setsStatusRevoked() {
        UUID addressId = UUID.randomUUID();
        AddressBook entry = new AddressBook();
        ReflectionTestUtils.setField(entry, "id", addressId);
        entry.setLabel("My Wallet");
        entry.setWalletAddress(WALLET);
        entry.setStatus(AddressStatus.ACTIVE);

        when(addressBookRepository.findById(addressId)).thenReturn(Optional.of(entry));

        service.revokeAddress(addressId, USER_ID);

        assertThat(entry.getStatus()).isEqualTo(AddressStatus.REVOKED);
        verify(addressBookRepository).save(entry);
        verify(auditLogRepository).save(any());
    }
}
