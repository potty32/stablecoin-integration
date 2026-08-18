package de.atruvia.stablecoin.storage.dev;

import de.atruvia.stablecoin.storage.ExportStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * Dev-Implementierung von ExportStorageService.
 * Schreibt Exporte ins lokale Dateisystem unter /tmp/stablecoin-exports/.
 *
 * Simuliert S3-Key-Struktur:
 *   /tmp/stablecoin-exports/{tenantId}/year={yyyy}/month={MM}/{type}-{iban}-{ts}.{ext}
 *
 * Presigned URLs zeigen auf den Dev-Download-Endpoint:
 *   http://localhost:8080/api/v1/dev/exports/download?token={base64token}&expires={epoch}
 *
 * Im Prod-Profil: durch AwsS3ExportService mit software.amazon.awssdk:s3 ersetzen.
 */
@Service
@Profile("dev")
public class DevExportStorageService implements ExportStorageService {

    private static final Logger log = LoggerFactory.getLogger(DevExportStorageService.class);
    private static final Path EXPORT_BASE = Path.of("/tmp/stablecoin-exports");
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter YEAR_FMT =
            DateTimeFormatter.ofPattern("yyyy").withZone(ZoneId.of("UTC"));
    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MM").withZone(ZoneId.of("UTC"));

    @Override
    public String upload(String tenantId, String iban, String exportType,
                         byte[] content, String extension) {
        Instant now = Instant.now();
        String safeIban = iban.replaceAll("[^A-Za-z0-9]", "");
        String ts = TS_FMT.format(now);

        // S3-Key-Struktur
        String s3Key = tenantId
                + "/year=" + YEAR_FMT.format(now)
                + "/month=" + MONTH_FMT.format(now)
                + "/" + exportType + "-" + safeIban + "-" + ts + "." + extension;

        Path targetPath = EXPORT_BASE.resolve(s3Key);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.write(targetPath, content);
            log.info("[DEV-S3] Upload → s3://stablecoin-exports/{} ({} bytes)", s3Key, content.length);
        } catch (IOException e) {
            throw new RuntimeException("DEV-S3 Upload fehlgeschlagen: " + e.getMessage(), e);
        }
        return s3Key;
    }

    @Override
    public String generatePresignedUrl(String s3Key, Duration validFor) {
        long expiresAt = Instant.now().plus(validFor).getEpochSecond();
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((s3Key + "|" + expiresAt).getBytes(StandardCharsets.UTF_8));
        String url = "http://localhost:8080/api/v1/dev/exports/download?token=" + token;
        log.info("[DEV-S3] Presigned URL generiert (gültig {}s): {}", validFor.getSeconds(), url);
        return url;
    }

    /**
     * Löst ein Presigned-URL-Token auf. Wird vom DevExportDownloadController verwendet.
     * @return [s3Key, expiresEpoch] oder null bei ungültigem Token
     */
    public static String[] resolveToken(String token) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length == 2) return parts;
        } catch (Exception ignored) { /* invalid */ }
        return null;
    }
}
