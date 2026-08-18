package de.atruvia.stablecoin;

import de.atruvia.stablecoin.exception.WebhookSignatureException;
import de.atruvia.stablecoin.service.compliance.WebhookSignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit-Tests für WebhookSignatureService.
 *
 * TC1: Dev-Profil — fehlende Signatur → Warnung, kein Fehler
 * TC2: Dev-Profil — beliebige Signatur → Warnung, kein Fehler (keine Prüfung)
 * TC3: Prod-Profil — fehlende Signatur → WebhookSignatureException (401 AUTH_002)
 * TC4: Prod-Profil — gültige HMAC-SHA256-Signatur → kein Fehler
 * TC5: Prod-Profil — ungültige Signatur → WebhookSignatureException (401 AUTH_002)
 */
class WebhookSecurityTest {

    private static final String TEST_SECRET = "test-circle-secret-key";
    private static final String TEST_BODY   = "{\"walletId\":\"0xABC\",\"amount\":1000}";

    private WebhookSignatureService serviceDevMode;
    private WebhookSignatureService serviceProdMode;

    @BeforeEach
    void setUp() {
        serviceDevMode = new WebhookSignatureService();
        ReflectionTestUtils.setField(serviceDevMode, "devMode", true);
        ReflectionTestUtils.setField(serviceDevMode, "circleSecret", TEST_SECRET);

        serviceProdMode = new WebhookSignatureService();
        ReflectionTestUtils.setField(serviceProdMode, "devMode", false);
        ReflectionTestUtils.setField(serviceProdMode, "circleSecret", TEST_SECRET);
    }

    // ── TC1 ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Dev-Profil: fehlende Signatur → kein Fehler (nur Warnung)")
    void devMode_missingSignature_noException() {
        // Should not throw — dev mode logs warning and proceeds
        serviceDevMode.verify(null, TEST_BODY);
        serviceDevMode.verify("", TEST_BODY);
    }

    // ── TC2 ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Dev-Profil: beliebige Signatur → keine Verifizierung, kein Fehler")
    void devMode_anySignature_noVerification() {
        // Even wrong signature should be ignored in dev mode
        serviceDevMode.verify("totally-wrong-signature", TEST_BODY);
    }

    // ── TC3 ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Prod-Profil: fehlende Signatur → WebhookSignatureException (AUTH_002)")
    void prodMode_missingSignature_throwsException() {
        assertThatThrownBy(() -> serviceProdMode.verify(null, TEST_BODY))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("X-Circle-Signature Header fehlt");

        assertThatThrownBy(() -> serviceProdMode.verify("", TEST_BODY))
                .isInstanceOf(WebhookSignatureException.class);
    }

    // ── TC4 ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Prod-Profil: gültige HMAC-SHA256-Signatur → Verifikation erfolgreich")
    void prodMode_validSignature_passes() throws Exception {
        String validSignature = computeHmac(TEST_SECRET, TEST_BODY);

        // Should not throw
        serviceProdMode.verify(validSignature, TEST_BODY);
    }

    // ── TC5 ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Prod-Profil: ungültige Signatur → WebhookSignatureException (AUTH_002)")
    void prodMode_invalidSignature_throwsException() {
        String wrongSignature = "0".repeat(64); // valid hex length but wrong content

        assertThatThrownBy(() -> serviceProdMode.verify(wrongSignature, TEST_BODY))
                .isInstanceOf(WebhookSignatureException.class)
                .hasMessageContaining("Ungültige Webhook-Signatur");
    }

    // ── Hilfsmethode ─────────────────────────────────────────────────────────

    /** HMAC-SHA256 identisch zur Produktion — für Testvektor-Erzeugung. */
    private String computeHmac(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
