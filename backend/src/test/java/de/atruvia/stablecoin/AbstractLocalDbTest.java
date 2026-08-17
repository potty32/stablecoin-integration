package de.atruvia.stablecoin;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Basis-Testklasse für Integrationstests ohne Docker.
 * Verwendet die lokal laufende PostgreSQL-Instanz (stablecoin_dev).
 * Kein Testcontainers — funktioniert in Kasm-Umgebung ohne Docker.
 */
@SpringBootTest
@ActiveProfiles("dev")
public abstract class AbstractLocalDbTest {

    @DynamicPropertySource
    static void configureLocalDb(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:postgresql://localhost:5432/stablecoin_dev");
        registry.add("spring.datasource.username", () -> "stablecoin");
        registry.add("spring.datasource.password", () -> "stablecoin_dev_pass");
    }
}
