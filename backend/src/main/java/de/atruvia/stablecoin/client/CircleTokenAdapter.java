package de.atruvia.stablecoin.client;

import de.atruvia.stablecoin.client.dto.AdapterTransferRequest;
import de.atruvia.stablecoin.client.dto.AdapterTransferResult;
import de.atruvia.stablecoin.client.dto.CircleTransactionStatusDto;
import de.atruvia.stablecoin.client.dto.CircleTransferRequestDto;
import de.atruvia.stablecoin.client.dto.CircleTransferResponseDto;
import de.atruvia.stablecoin.entity.StablecoinCurrency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Token-Adapter für Circle (USDC/EURC).
 *
 * Kapselt submit + poll-Zyklus gegen die Circle Web3 Services API.
 * Im Dev-Profil: MockCircleWalletClient wird injiziert (3s Delay + synthetic Hash).
 * Im Prod-Profil: HttpCircleWalletClient (echte Circle API).
 */
@Component
public class CircleTokenAdapter implements StablecoinTokenAdapter {

    private static final Logger log = LoggerFactory.getLogger(CircleTokenAdapter.class);
    private static final String MASTER_WALLET = "BANK_MASTER_WALLET_ID";

    private final CircleWalletClient circleWalletClient;

    public CircleTokenAdapter(CircleWalletClient circleWalletClient) {
        this.circleWalletClient = circleWalletClient;
    }

    @Override
    public Set<StablecoinCurrency> supportedCurrencies() {
        return Set.of(StablecoinCurrency.USDC, StablecoinCurrency.EURC);
    }

    @Override
    public String getAdapterName() {
        return "Circle-USDC/EURC";
    }

    @Override
    public AdapterTransferResult initiateAndConfirm(AdapterTransferRequest request) {
        CircleTransferResponseDto init = circleWalletClient.initiateTransfer(new CircleTransferRequestDto(
                request.idempotencyKey(),
                new CircleTransferRequestDto.Source("wallet", MASTER_WALLET),
                new CircleTransferRequestDto.Destination("blockchain", request.destinationWallet(), "MATIC"),
                new CircleTransferRequestDto.Amount(request.amount().toPlainString(), request.currency().name())));

        log.info("[CIRCLE-ADAPTER] Transfer initiiert: circleId={}", init.id());

        CircleTransactionStatusDto status = circleWalletClient.getTransactionStatus(init.id());
        if (!"COMPLETE".equals(status.status())) {
            throw new IllegalStateException("Circle Transfer nicht COMPLETE: " + status.status());
        }

        log.info("[CIRCLE-ADAPTER] Transfer bestätigt: circleId={} hash={}", init.id(), status.transactionHash());
        return new AdapterTransferResult(init.id(), status.transactionHash());
    }

    @Override
    public AdapterTransferResult initiateReturn(AdapterTransferRequest request) {
        CircleTransferResponseDto init = circleWalletClient.initiateTransfer(new CircleTransferRequestDto(
                request.idempotencyKey(),
                new CircleTransferRequestDto.Source("wallet", MASTER_WALLET),
                new CircleTransferRequestDto.Destination("blockchain", request.destinationWallet(), "POLYGON"),
                new CircleTransferRequestDto.Amount(request.amount().toPlainString(), request.currency().name())));

        log.info("[CIRCLE-ADAPTER] Return-Transfer initiiert: circleId={}", init.id());
        return new AdapterTransferResult(init.id(), init.transactionHash());
    }
}
