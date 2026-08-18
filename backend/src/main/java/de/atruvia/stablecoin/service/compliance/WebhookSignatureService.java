package de.atruvia.stablecoin.service.compliance;

import de.atruvia.stablecoin.exception.WebhookSignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Verifiziert HMAC-SHA256-Signaturen eingehender Circle/Taurus-Webhooks.
 *
 * Dev-Profil:  Fehlender Header erzeugt eine Warnung — Anfrage wird nicht blockiert.
 * Prod-Profil: Fehlende oder ungültige X-Circle-Signature → HTTP 401 (AUTH_002).
 */
@Service
public class WebhookSignatureService {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureService.class);
    private static final String ALGORITHM = "HmacSHA256";

    @Value("${app.webhook.circle-secret:}")
    private String circleSecret;

    @Value("${app.security.dev-mode:false}")
    private boolean devMode;

    /**
     * Prüft die Signatur des Webhook-Requests.
     *
     * @param signature Wert des X-Circle-Signature Headers (darf null sein)
     * @param rawBody   Roher Request-Body als String (für HMAC-Berechnung)
     */
    public void verify(String signature, String rawBody) {
        if (devMode) {
            if (signature == null || signature.isBlank()) {
                log.warn("[WEBHOOK-SEC] X-Circle-Signature fehlt — im Dev-Profil ignoriert");
            }
            return;
        }

        if (signature == null || signature.isBlank()) {
            log.error("[WEBHOOK-SEC] X-Circle-Signature Header fehlt — Anfrage abgelehnt");
            throw new WebhookSignatureException("X-Circle-Signature Header fehlt");
        }

        String expected = computeHmac(rawBody);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            log.error("[WEBHOOK-SEC] Ungültige Webhook-Signatur — Anfrage abgelehnt");
            throw new WebhookSignatureException("Ungültige Webhook-Signatur (X-Circle-Signature)");
        }

        log.debug("[WEBHOOK-SEC] Signatur gültig");
    }

    private String computeHmac(String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    circleSecret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(keySpec);
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-Berechnung fehlgeschlagen", e);
        }
    }
}
