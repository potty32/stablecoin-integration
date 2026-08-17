package de.atruvia.stablecoin.client.mock;

import de.atruvia.stablecoin.client.N8nWebhookClient;
import de.atruvia.stablecoin.client.dto.SettlementNotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev")
public class MockN8nWebhookClient implements N8nWebhookClient {

    private static final Logger log = LoggerFactory.getLogger(MockN8nWebhookClient.class);

    @Override
    public void notifySettlement(SettlementNotificationDto notification) {
        log.info("[MOCK n8n] Settlement notification received: txId={} revenue={}",
                notification.transactionId(), notification.grossRevenue());
    }

    @Override
    public void notifyAddressRevoked(String walletAddress, String customerId, String riskScore) {
        log.warn("[MOCK n8n] Address revoked by sanctions batch: wallet={} customer={} riskScore={}",
                walletAddress, customerId, riskScore);
    }
}
