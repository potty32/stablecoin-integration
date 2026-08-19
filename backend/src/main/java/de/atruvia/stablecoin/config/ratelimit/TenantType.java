package de.atruvia.stablecoin.config.ratelimit;

public enum TenantType {
    SMALL_VB,   // Kleine Volksbank:  20 req/min
    LARGE_VB,   // Große Volksbank:  100 req/min
    MARKTBANK   // Marktbank:        500 req/min
}
