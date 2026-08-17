package de.atruvia.stablecoin;

import de.atruvia.stablecoin.dto.response.BulkPaymentResult;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import de.atruvia.stablecoin.entity.TransactionStatus;
import de.atruvia.stablecoin.entity.TransactionType;
import de.atruvia.stablecoin.service.b2b.B2bTransferService;
import de.atruvia.stablecoin.service.b2b.BulkPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class BulkPaymentServiceTest {

    @Mock private B2bTransferService transferService;

    private BulkPaymentService bulkPaymentService;

    private static final String VALID_WALLET = "0xAbCdEf1234567890ABcDeF1234567890aBcDeF12";
    private static final String SOURCE_IBAN = "DE89370400440532013000";
    private static final String INITIATOR_ID = "user-bulk-01";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        bulkPaymentService = new BulkPaymentService(transferService);
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile(
                "file", "bulk.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private TransactionResponse stubResponse() {
        return new TransactionResponse(
                UUID.randomUUID(), TransactionType.OUTBOUND, TransactionStatus.CREATED,
                new BigDecimal("500.00"), new BigDecimal("500.000000"),
                StablecoinCurrency.USDC, null, BigDecimal.ZERO, false,
                LocalDateTime.now(), null, List.of());
    }

    @Test
    @DisplayName("TC-B-01: header-only CSV -> 0 total, 0 successful, 0 failed")
    void headerOnly_zeroResults() {
        String csv = "destinationWallet,amountEur,currency,reference\n";

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.total()).isEqualTo(0);
        assertThat(result.successful()).isEqualTo(0);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    @DisplayName("TC-B-02: single valid row -> 1 successful transaction created")
    void singleValidRow_oneSuccess() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",500.00,USDC,REF-001\n";
        when(transferService.initiate(anyString(), any(), anyString())).thenReturn(stubResponse());

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(0);
        assertThat(result.rows().get(0).status()).isEqualTo("OK");
    }

    @Test
    @DisplayName("TC-B-03: invalid wallet address -> error row")
    void invalidWalletAddress_errorRow() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + "notAnEthAddress,500.00,USDC,REF-002\n";

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(0);
        assertThat(result.rows().get(0).status()).isEqualTo("ERROR");
        assertThat(result.rows().get(0).message()).contains("Invalid destination wallet address");
    }

    @Test
    @DisplayName("TC-B-04: non-numeric amount -> error row")
    void nonNumericAmount_errorRow() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",abc,USDC,REF-003\n";

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.rows().get(0).message()).contains("Invalid amount value");
    }

    @Test
    @DisplayName("TC-B-05: negative amount -> error row")
    void negativeAmount_errorRow() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",-100.00,USDC,REF-004\n";

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.rows().get(0).message()).contains("Amount must be positive");
    }

    @Test
    @DisplayName("TC-B-06: unknown currency -> error row")
    void unknownCurrency_errorRow() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",500.00,BITCOIN,REF-005\n";

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.rows().get(0).message()).contains("Unknown currency");
    }

    @Test
    @DisplayName("TC-B-07: too few columns -> error row")
    void tooFewColumns_errorRow() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",500.00\n";

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.rows().get(0).message()).contains("Invalid CSV format");
    }

    @Test
    @DisplayName("TC-B-08: transferService throws -> row marked ERROR, processing continues")
    void transferServiceThrows_rowMarkedError() {
        String wallet2 = "0x1234567890AbCdEf1234567890abCDEF12345678";
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",500.00,USDC,REF-006\n"
                + wallet2 + ",300.00,USDC,REF-007\n";
        when(transferService.initiate(anyString(), any(), anyString()))
                .thenThrow(new RuntimeException("transfer failed"))
                .thenReturn(stubResponse());

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.rows().get(0).status()).isEqualTo("ERROR");
        assertThat(result.rows().get(1).status()).isEqualTo("OK");
    }

    @Test
    @DisplayName("TC-B-09: multiple valid rows -> all successful")
    void multipleValidRows_allSuccessful() {
        String wallet2 = "0x1234567890AbCdEf1234567890abCDEF12345678";
        String wallet3 = "0xDEADBEEFDeadBeefDeadBeefDEADBEEFdeadbeef";
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",100.00,USDC,REF-A\n"
                + wallet2 + ",200.00,EURC,REF-B\n"
                + wallet3 + ",300.00,USDC,REF-C\n";
        when(transferService.initiate(anyString(), any(), anyString())).thenReturn(stubResponse());

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.total()).isEqualTo(3);
        assertThat(result.successful()).isEqualTo(3);
        assertThat(result.failed()).isEqualTo(0);
    }

    @Test
    @DisplayName("TC-B-10: mix of valid and invalid rows -> correct total/success/failure counts")
    void mixedRows_correctCounts() {
        String csv = "destinationWallet,amountEur,currency,reference\n"
                + VALID_WALLET + ",500.00,USDC,REF-GOOD\n"  // valid
                + "0xinvalidaddr,500.00,USDC,REF-BAD\n";    // invalid wallet
        when(transferService.initiate(anyString(), any(), anyString())).thenReturn(stubResponse());

        BulkPaymentResult result = bulkPaymentService.process(csvFile(csv), SOURCE_IBAN, INITIATOR_ID);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.successful()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
    }
}
