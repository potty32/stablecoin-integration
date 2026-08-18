package de.atruvia.stablecoin.storage.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Dev-only: Dient lokal gespeicherte Exporte aus /tmp/stablecoin-exports/ aus.
 * Simuliert die Funktion von S3-Presigned-URLs im dev-Modus.
 *
 * GET /api/v1/dev/exports/download?token={presigned-token}
 *
 * Token-Format: Base64(s3Key + "|" + expiresEpoch)
 * Nur aktiv im dev-Profil.
 */
@RestController
@RequestMapping("/api/v1/dev/exports")
@Profile("dev")
@ConditionalOnProperty(name = "app.security.dev-mode", havingValue = "true")
public class DevExportDownloadController {

    private static final Logger log = LoggerFactory.getLogger(DevExportDownloadController.class);
    private static final Path EXPORT_BASE = Path.of("/tmp/stablecoin-exports");

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String token) {
        String[] parts = DevExportStorageService.resolveToken(token);
        if (parts == null) {
            return ResponseEntity.badRequest().build();
        }

        String s3Key = parts[0];
        long expiresAt = Long.parseLong(parts[1]);

        if (Instant.now().getEpochSecond() > expiresAt) {
            log.warn("[DEV-S3] Presigned URL abgelaufen: {}", s3Key);
            return ResponseEntity.status(403).build();
        }

        Path filePath = EXPORT_BASE.resolve(s3Key);
        if (!filePath.startsWith(EXPORT_BASE)) {
            return ResponseEntity.badRequest().build(); // Path-Traversal-Schutz
        }
        if (!Files.exists(filePath)) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] content = Files.readAllBytes(filePath);
            String filename = filePath.getFileName().toString();
            MediaType mediaType = filename.endsWith(".xml")
                    ? MediaType.APPLICATION_XML
                    : MediaType.parseMediaType("text/csv;charset=UTF-8");

            log.info("[DEV-S3] Download: {} ({} bytes)", s3Key, content.length);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /** Liste alle verfügbaren Exporte im Dev-Verzeichnis. */
    @GetMapping("/list")
    public ResponseEntity<Object> listExports() throws IOException {
        if (!Files.exists(EXPORT_BASE)) {
            return ResponseEntity.ok(java.util.List.of());
        }
        var files = Files.walk(EXPORT_BASE)
                .filter(Files::isRegularFile)
                .map(p -> {
                    long sz = -1;
                    String mod = "";
                    try { sz = Files.size(p); } catch (IOException ignored) {}
                    try { mod = Files.getLastModifiedTime(p).toInstant().toString(); } catch (IOException ignored) {}
                    return java.util.Map.of(
                            "s3Key", EXPORT_BASE.relativize(p).toString(),
                            "size", sz,
                            "lastModified", mod);
                })
                .toList();
        return ResponseEntity.ok(files);
    }
}
