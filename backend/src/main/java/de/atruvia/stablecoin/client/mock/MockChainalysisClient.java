package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.ChainalysisClient;
import de.atruvia.stablecoin.client.dto.AddressScreenRequestDto;
import de.atruvia.stablecoin.client.dto.AddressScreenResponseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("dev")
public class MockChainalysisClient implements ChainalysisClient {

    private static final String HIGH_RISK_ADDRESS = "0xDEAD000000000000000000000000000000000000";

    @Override
    public AddressScreenResponseDto screenAddress(AddressScreenRequestDto request) {
        if (HIGH_RISK_ADDRESS.equalsIgnoreCase(request.address())) {
            return new AddressScreenResponseDto(
                    request.address(), "HIGH",
                    List.of("SANCTIONS", "DARKNET_MARKET"),
                    true, false
            );
        }
        return new AddressScreenResponseDto(request.address(), "LOW", List.of(), false, true);
    }
}
