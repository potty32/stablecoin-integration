package de.atruvia.stablecoin.controller;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Date;
import java.util.Map;

/**
 * Dev-only Endpunkt für JWT-Generierung mit Tenant-Claim.
 * Nur aktiv wenn app.security.dev-mode=true.
 */
@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "app.security.dev-mode", havingValue = "true")
public class DevAuthController {

    @Value("${app.security.jwt-secret}")
    private String jwtSecret;

    @GetMapping("/dev-token")
    public ResponseEntity<Map<String, String>> getDevToken(
            @RequestParam String customerId,
            @RequestParam(defaultValue = "tenant-default") String tenant) {

        long now = System.currentTimeMillis();
        long expiry = now + 86_400_000L; // 24h

        String token = Jwts.builder()
                .subject(customerId)
                .claim("tenant", tenant)
                .issuedAt(new Date(now))
                .expiration(new Date(expiry))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

        return ResponseEntity.ok(Map.of("token", token, "tenant", tenant, "customerId", customerId));
    }
}
