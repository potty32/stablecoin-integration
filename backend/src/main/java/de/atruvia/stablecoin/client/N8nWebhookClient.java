package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.SettlementNotificationDto;

public interface N8nWebhookClient {
    void notifySettlement(SettlementNotificationDto notification);
    void notifyAddressRevoked(String walletAddress, String customerId, String riskScore);
}
