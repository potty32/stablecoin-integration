package de.atruvia.stablecoin.service.b2c;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * G-14: Sicheres Hashing von Telefonnummern für das P2P-Alias-System.
 *
 * Ersetzt den statisch codierten SHA-256-Salt ("atruvia-stablecoin-2026") durch
 * HMAC-SHA256 mit einem konfigurierbaren serverseitigen Schlüssel.
 *
 * In Prod: PHONE_HMAC_KEY Umgebungsvariable setzen (mind. 32 zufällige Bytes, hex-kodiert).
 * In Dev:  Default-Key "atruvia-stablecoin-2026" für Rückwärtskompatibilität.
 *
 * DSGVO Art. 32: Personenbezogene Daten (Telefonnummern) müssen durch geeignete
 * kryptographische Maßnahmen geschützt werden.
 */
@Service
public class PhoneHashService {

    private static final Logger log = LoggerFactory.getLogger(PhoneHashService.class);
    private static final String ALGORITHM = "HmacSHA256";

    @Value("${app.security.phone-hmac-key:atruvia-stablecoin-2026}")
    private String hmacKey;

    /**
     * Berechnet einen deterministischen HMAC-SHA256-Hash der normalisierten Telefonnummer.
     * Deterministisch = gleiche Nummer erzeugt immer gleichen Hash (für DB-Lookup nötig).
     *
     * @param phoneNumber Telefonnummer (internationales Format empfohlen: +49...)
     * @return 64-stelliger Hex-String (256 Bit)
     */
    public String hash(String phoneNumber) {
        String normalized = normalize(phoneNumber);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(
                    hmacKey.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256-Berechnung für Telefonnummer fehlgeschlagen", e);
        }
    }

    /**
     * Normalisiert Telefonnummern für konsistente Hashes
     * (entfernt Leerzeichen, Klammern, Bindestriche; behält +).
     */
    private String normalize(String phoneNumber) {
        if (phoneNumber == null) throw new IllegalArgumentException("Telefonnummer darf nicht null sein");
        return phoneNumber.replaceAll("[^0-9+]", "");
    }
}
