package de.atruvia.stablecoin.storage;

import java.time.Duration;

/**
 * Abstraktionsschicht für S3-kompatiblen Objekt-Speicher (Export-Dateien).
 *
 * S3-Key-Struktur:
 *   {tenantId}/year={yyyy}/month={MM}/{exportType}-{iban}-{timestamp}.[xml|csv]
 *
 * Bucket: stablecoin-exports
 *
 * Implementierungen:
 *   dev-Profil:  DevExportStorageService → schreibt nach /tmp/stablecoin-exports/
 *   prod-Profil: AwsS3ExportService / CephExportService → echte S3-API (software.amazon.awssdk)
 */
public interface ExportStorageService {

    /**
     * Lädt Export-Inhalt in den S3-Bucket und gibt den S3-Key zurück.
     *
     * @param tenantId   Mandant (für Bucket-Partitionierung und RLS)
     * @param iban       Konto-IBAN für Dateiname
     * @param exportType "camt053", "camt054", "camt029", "datev"
     * @param content    Dateiinhalt (XML/CSV)
     * @param extension  "xml" oder "csv"
     * @return S3-Key (z.B. "tenant-kleine-vb/year=2026/month=08/camt053-DE89...2026-08-18T10-00-00.xml")
     */
    String upload(String tenantId, String iban, String exportType, byte[] content, String extension);

    /**
     * Generiert eine signierte Download-URL (Presigned URL) mit TTL-Ablauf.
     *
     * @param s3Key   Rückgabewert von upload()
     * @param validFor  Gültigkeitsdauer (Standard: 15 Minuten)
     * @return Presigned URL (in dev: lokaler HTTP-Endpoint, in prod: echte AWS/Ceph-URL)
     */
    String generatePresignedUrl(String s3Key, Duration validFor);
}
