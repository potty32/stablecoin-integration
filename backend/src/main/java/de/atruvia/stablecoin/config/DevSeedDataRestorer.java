package de.atruvia.stablecoin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Stellt beim Start im dev-Profil fehlende Address-Book-Seed-Einträge wieder her.
 *
 * Hintergrund: Tests, die deleteAllInBatch() auf address_book aufrufen,
 * entfernen auch die V24-Seed-Daten. Dieser Runner prüft beim Start ob
 * die bekannten Seed-Einträge (stabile IDs) noch vorhanden sind und
 * fügt sie bei Bedarf neu ein.
 */
@Component
@ConditionalOnProperty(name = "app.security.dev-mode", havingValue = "true")
public class DevSeedDataRestorer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeedDataRestorer.class);

    private final JdbcTemplate adminJdbc;

    public DevSeedDataRestorer(@Qualifier("adminJdbcTemplate") JdbcTemplate adminJdbc) {
        this.adminJdbc = adminJdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        restoreAddressBook();
        log.info("[DEV-SEED] Address-Book-Seed-Prüfung abgeschlossen.");
    }

    private void restoreAddressBook() {
        // Kleine VB — cust-b2b-001 (ID: a0000001...)
        upsertAddressBook("c0000001-0000-0000-0000-000000000001",
                "a0000001-0000-0000-0000-000000000001",
                "Hauptpartner GmbH (USDC)",
                "0xA100000000000000000000000000000000000001",
                "USDC", "LOW", "tenant-kleine-vb");
        upsertAddressBook("c0000002-0000-0000-0000-000000000001",
                "a0000001-0000-0000-0000-000000000001",
                "EU-Lieferant (EURC)",
                "0xA200000000000000000000000000000000000001",
                "EURC", "LOW", "tenant-kleine-vb");
        upsertAddressBook("c0000003-0000-0000-0000-000000000001",
                "a0000001-0000-0000-0000-000000000001",
                "Sanktionierte Adresse (Compliance-Testfall)",
                "0xDEAD000000000000000000000000000000000000",
                "USDC", "HIGH", "tenant-kleine-vb");

        // Grosse VB — cust-b2b-001 (ID: b0000001...)
        upsertAddressBook("d0000001-0000-0000-0000-000000000001",
                "b0000001-0000-0000-0000-000000000001",
                "Metropole Partner AG (USDC)",
                "0xB100000000000000000000000000000000000001",
                "USDC", "LOW", "tenant-grosse-vb");
        upsertAddressBook("d0000002-0000-0000-0000-000000000001",
                "b0000001-0000-0000-0000-000000000001",
                "Interbanken-EURC Empfänger",
                "0xB200000000000000000000000000000000000001",
                "EURC", "LOW", "tenant-grosse-vb");
    }

    private void upsertAddressBook(String id, String accountId, String label,
                                    String wallet, String currency, String risk, String tenantId) {
        int updated = adminJdbc.update("""
                INSERT INTO address_book
                    (id, customer_account_id, label, wallet_address, currency,
                     risk_score, status, verified_at, tenant_id)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'ACTIVE', NOW(), ?)
                ON CONFLICT (id) DO NOTHING
                """, id, accountId, label, wallet, currency, risk, tenantId);
        if (updated > 0) {
            log.info("[DEV-SEED] Address-Book-Eintrag wiederhergestellt: {} ({})", label, tenantId);
        }
    }
}
