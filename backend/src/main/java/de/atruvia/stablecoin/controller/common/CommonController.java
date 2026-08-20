package de.atruvia.stablecoin.controller.common;

import de.atruvia.stablecoin.dto.response.AccountBalanceResponse;
import de.atruvia.stablecoin.dto.response.TransactionResponse;
import de.atruvia.stablecoin.service.common.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * T-05-Fix: Thin Controller — keine Business-Logik, keine Repository-Direktaufrufe.
 * Ownership-Check, Balance-Aggregation und Timeline-Building delegiert an AccountService.
 */
@RestController
@RequestMapping("/api/v1")
public class CommonController {

    private final AccountService accountService;

    public CommonController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping("/accounts/{iban}/balance")
    public ResponseEntity<AccountBalanceResponse> getBalance(
            @PathVariable String iban,
            Authentication auth) {
        return ResponseEntity.ok(accountService.getBalanceForCustomer(iban, auth.getName()));
    }

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(
            @PathVariable UUID id,
            Authentication auth) {
        return ResponseEntity.ok(accountService.getTransactionForCustomer(id, auth.getName()));
    }
}
