package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.TaurusCustodyClient;
import de.atruvia.stablecoin.client.dto.TaurusTransactionRequestDto;
import de.atruvia.stablecoin.client.dto.TaurusTransactionResponseDto;
import de.atruvia.stablecoin.exception.TaurusLimitExceededException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Profile("dev")
public class MockTaurusCustodyClient implements TaurusCustodyClient {

    private static final BigDecimal SINGLE_TX_LIMIT = new BigDecimal("1000000.00");

    @Override
    public TaurusTransactionResponseDto signAndSubmit(TaurusTransactionRequestDto request) {
        BigDecimal amount = new BigDecimal(request.amount());
        if (amount.compareTo(SINGLE_TX_LIMIT) > 0) {
            throw new TaurusLimitExceededException(
                    "Transaction exceeds single-transaction custody limit 1,000,000 EUR. Requested: " + request.amount()
            );
        }
        String id = "taurus-tx-" + UUID.randomUUID().toString().substring(0, 8);
        return new TaurusTransactionResponseDto(id, "SUBMITTED", "0xSignedPayload-" + id, LocalDateTime.now());
    }
}
