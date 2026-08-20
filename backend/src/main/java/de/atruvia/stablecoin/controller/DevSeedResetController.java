package de.atruvia.stablecoin.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dev-only Endpunkt zum Zurücksetzen und Neuladen aller Testdaten.
 * Löscht alle transaktionalen Daten der Dev-Mandanten und setzt den
 * Address-Book-Seed zurück — Konten und Tenant-Settings bleiben erhalten.
 */
@RestController
@RequestMapping("/api/v1/dev/seed")
@ConditionalOnProperty(name = "app.security.dev-mode", havingValue = "true")
public class DevSeedResetController {

    private static final Logger log = LoggerFactory.getLogger(DevSeedResetController.class);

    private static final String[] DEV_TENANTS = {
        "tenant-kleine-vb", "tenant-grosse-vb", "tenant-default"
    };

    private final JdbcTemplate adminJdbc;

    public DevSeedResetController(@Qualifier("adminJdbcTemplate") JdbcTemplate adminJdbc) {
        this.adminJdbc = adminJdbc;
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        log.warn("[DEV-SEED-RESET] Testdaten werden zurückgesetzt...");

        int deleted = 0;
        deleted += truncateTenantData();
        int inserted = restoreAddressBook();

        log.info("[DEV-SEED-RESET] Fertig: {} Zeilen gelöscht, {} Address-Book-Einträge wiederhergestellt.",
                deleted, inserted);

        return ResponseEntity.ok(Map.of(
                "status", "OK",
                "message", "Testdaten zurückgesetzt",
                "deletedRows", deleted,
                "addressBookEntries", inserted
        ));
    }

    private int truncateTenantData() {
        String tenantPlaceholders = "'" + String.join("','", DEV_TENANTS) + "'";
        int total = 0;

        // Reihenfolge: FK-Abhängigkeiten beachten
        total += adminJdbc.update("DELETE FROM dvp_escrow WHERE tenant_id IN (" + tenantPlaceholders + ")");
        total += adminJdbc.update("DELETE FROM outbox_message WHERE transaction_id IN " +
                "(SELECT id FROM stablecoin_transaction WHERE tenant_id IN (" + tenantPlaceholders + "))");
        total += adminJdbc.update("DELETE FROM audit_log WHERE tenant_id IN (" + tenantPlaceholders + ")");
        total += adminJdbc.update("DELETE FROM approval_workflow WHERE transaction_id IN " +
                "(SELECT id FROM stablecoin_transaction WHERE tenant_id IN (" + tenantPlaceholders + "))");
        total += adminJdbc.update("DELETE FROM stablecoin_transaction WHERE tenant_id IN (" + tenantPlaceholders + ")");
        total += adminJdbc.update("DELETE FROM address_book WHERE tenant_id IN (" + tenantPlaceholders + ")");
        total += adminJdbc.update("DELETE FROM phone_alias WHERE tenant_id IN (" + tenantPlaceholders + ")");
        total += adminJdbc.update("DELETE FROM rate_quote WHERE customer_account_id IN " +
                "(SELECT id FROM customer_account WHERE tenant_id IN (" + tenantPlaceholders + "))");

        log.info("[DEV-SEED-RESET] {} transaktionale Zeilen gelöscht.", total);
        return total;
    }

    private int restoreAddressBook() {
        int total = 0;
        total += upsert("c0000001-0000-0000-0000-000000000001",
                "a0000001-0000-0000-0000-000000000001",
                "Hauptpartner GmbH (USDC)",
                "0xA100000000000000000000000000000000000001", "USDC", "LOW", "tenant-kleine-vb");
        total += upsert("c0000002-0000-0000-0000-000000000001",
                "a0000001-0000-0000-0000-000000000001",
                "EU-Lieferant (EURC)",
                "0xA200000000000000000000000000000000000001", "EURC", "LOW", "tenant-kleine-vb");
        total += upsert("c0000003-0000-0000-0000-000000000001",
                "a0000001-0000-0000-0000-000000000001",
                "Sanktionierte Adresse (Compliance-Testfall)",
                "0xDEAD000000000000000000000000000000000000", "USDC", "HIGH", "tenant-kleine-vb");
        total += upsert("d0000001-0000-0000-0000-000000000001",
                "b0000001-0000-0000-0000-000000000001",
                "Metropole Partner AG (USDC)",
                "0xB100000000000000000000000000000000000001", "USDC", "LOW", "tenant-grosse-vb");
        total += upsert("d0000002-0000-0000-0000-000000000001",
                "b0000001-0000-0000-0000-000000000001",
                "Interbanken-EURC Empfänger",
                "0xB200000000000000000000000000000000000001", "EURC", "LOW", "tenant-grosse-vb");
        return total;
    }

    private int upsert(String id, String accountId, String label,
                       String wallet, String currency, String risk, String tenantId) {
        return adminJdbc.update("""
                INSERT INTO address_book
                    (id, customer_account_id, label, wallet_address, currency,
                     risk_score, status, verified_at, tenant_id)
                VALUES (?::uuid, ?::uuid, ?, ?, ?, ?, 'ACTIVE', NOW(), ?)
                ON CONFLICT (id) DO UPDATE SET
                    label = EXCLUDED.label,
                    wallet_address = EXCLUDED.wallet_address,
                    status = 'ACTIVE',
                    verified_at = NOW()
                """, id, accountId, label, wallet, currency, risk, tenantId);
    }
}
