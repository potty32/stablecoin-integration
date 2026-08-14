package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.TaurusTransactionRequestDto;
import de.atruvia.stablecoin.client.dto.TaurusTransactionResponseDto;

public interface TaurusCustodyClient {
    TaurusTransactionResponseDto signAndSubmit(TaurusTransactionRequestDto request);
}
