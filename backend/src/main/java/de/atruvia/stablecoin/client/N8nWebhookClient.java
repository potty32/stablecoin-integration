package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.SettlementNotificationDto;

public interface N8nWebhookClient {
    void notifySettlement(SettlementNotificationDto notification);
    void notifyAddressRevoked(String walletAddress, String customerId, String riskScore);

    /** G-11: Alerting bei steckengebliebenen Outbox-Nachrichten (>Schwelle Minuten). */
    void notifyOutboxAlert(int stuckCount, int thresholdMinutes, String oldestMessageId);
}
