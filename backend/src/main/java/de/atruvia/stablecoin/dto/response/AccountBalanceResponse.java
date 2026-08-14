package de.atruvia.stablecoin.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record AccountBalanceResponse(
        String iban,
        BigDecimal balanceEur,
        Map<String, String> stablecoinBalances
) {}
