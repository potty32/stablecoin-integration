package de.atruvia.stablecoin.dto.request.b2b;

/**
 * Request-Body für Kill-Switch-Aktivierung (G-07).
 *
 * @param scope  "GLOBAL" oder Tenant-ID
 * @param reason Pflichtfeld: Begründung (für Audit Trail)
 */
public record KillSwitchRequest(String scope, String reason) {}
