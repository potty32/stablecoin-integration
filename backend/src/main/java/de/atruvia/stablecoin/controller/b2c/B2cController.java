package de.atruvia.stablecoin.controller.b2c;

import de.atruvia.stablecoin.dto.request.b2c.MicropaymentRequest;
import de.atruvia.stablecoin.dto.request.b2c.P2pPhoneRequest;
import de.atruvia.stablecoin.dto.request.b2c.RegisterPhoneAliasRequest;
import de.atruvia.stablecoin.dto.request.b2c.RemittanceRequest;
import de.atruvia.stablecoin.dto.request.b2c.YieldDepositRequest;
import de.atruvia.stablecoin.dto.response.CardWalletResponse;
import de.atruvia.stablecoin.dto.response.RemittanceResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.dto.response.YieldPositionResponse;
import de.atruvia.stablecoin.service.b2c.B2cMicropaymentService;
import de.atruvia.stablecoin.service.b2c.B2cP2pService;
import de.atruvia.stablecoin.service.b2c.B2cRemittanceService;
import de.atruvia.stablecoin.service.b2c.B2cYieldService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/b2c")
@Validated
public class B2cController {

    private final B2cRemittanceService remittanceService;
    private final B2cP2pService p2pService;
    private final B2cYieldService yieldService;
    private final B2cMicropaymentService micropaymentService;

    public B2cController(
            B2cRemittanceService remittanceService,
            B2cP2pService p2pService,
            B2cYieldService yieldService,
            B2cMicropaymentService micropaymentService) {
        this.remittanceService = remittanceService;
        this.p2pService = p2pService;
        this.yieldService = yieldService;
        this.micropaymentService = micropaymentService;
    }

    @PostMapping("/remittances")
    public ResponseEntity<RemittanceResponse> sendRemittance(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid RemittanceRequest request,
            Authentication auth) {
        return ResponseEntity.ok(remittanceService.send(idempotencyKey, request, auth.getName()));
    }

    @PostMapping("/p2p/phone")
    public ResponseEntity<TransactionResponse> sendToPhone(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid P2pPhoneRequest request,
            Authentication auth) {
        return ResponseEntity.ok(p2pService.sendToPhone(idempotencyKey, request, auth.getName()));
    }

    @PostMapping("/p2p/phone/register")
    public ResponseEntity<Void> registerPhoneAlias(
            @RequestBody @Valid RegisterPhoneAliasRequest request,
            Authentication auth) {
        p2pService.registerPhoneAlias(request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/savings/yield/deposit")
    public ResponseEntity<YieldPositionResponse> yieldDeposit(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid YieldDepositRequest request,
            Authentication auth) {
        return ResponseEntity.ok(yieldService.deposit(idempotencyKey, request, auth.getName()));
    }

    @DeleteMapping("/savings/yield/{id}")
    public ResponseEntity<Void> yieldRedeem(@PathVariable String id, Authentication auth) {
        yieldService.redeem(UUID.fromString(id), auth.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/savings/yield")
    public ResponseEntity<YieldPositionResponse> getYieldPosition(Authentication auth) {
        return ResponseEntity.ok(yieldService.getPosition(auth.getName()));
    }

    @GetMapping("/card/wallet")
    public ResponseEntity<CardWalletResponse> getCardWallet(Authentication auth) {
        return ResponseEntity.ok(micropaymentService.getCardWallet(auth.getName()));
    }

    @PostMapping("/micropayments")
    public ResponseEntity<TransactionResponse> micropayment(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid MicropaymentRequest request,
            Authentication auth) {
        return ResponseEntity.ok(micropaymentService.pay(idempotencyKey, request, auth.getName()));
    }
}
