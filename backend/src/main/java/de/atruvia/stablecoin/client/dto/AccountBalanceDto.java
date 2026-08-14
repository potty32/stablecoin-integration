package de.atruvia.stablecoin.client.dto;

import java.math.BigDecimal;

public record AccountBalanceDto(String iban, BigDecimal balanceEur, String currency) {}
