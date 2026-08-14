package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;

public interface ChainalysisClient {
    AddressScreenResponseDto screenAddress(AddressScreenRequestDto request);
}
