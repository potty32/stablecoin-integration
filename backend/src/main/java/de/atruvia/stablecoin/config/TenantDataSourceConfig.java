package de.atruvia.stablecoin.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import javax.sql.DataSource;

/**
 * Ersetzt die Spring-Boot-Auto-DataSource durch einen TenantAwareDataSource-Proxy.
 * Der Proxy setzt app.current_tenant auf jeder Connection bei Pool-Entnahme.
 *
 * Zwei Datasources:
 *  - hikariBaseDataSource: roher HikariCP-Pool
 *  - dataSource (Primary): Tenant-Proxy-Wrapper → von JPA und Flyway genutzt
 *
 * Railway: nur ein DB-User → adminDataSource nutzt dieselben Credentials wie datasource.
 * Dev: spring.flyway.user/password können abweichen (Owner-User für BYPASSRLS).
 */
@Configuration
public class TenantDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties tenantDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource hikariBaseDataSource(DataSourceProperties tenantDataSourceProperties) {
        return tenantDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikariBaseDataSource) {
        return new TenantAwareDataSource(hikariBaseDataSource);
    }

    /**
     * Admin-DataSource für Cross-Tenant-Lookups (OutboxProcessor, Inbound-Webhook).
     * Fallback auf spring.datasource.* wenn spring.flyway.* nicht gesetzt (Railway).
     */
    @Bean("adminDataSource")
    public DataSource adminDataSource(
            @Value("${spring.flyway.url:${spring.datasource.url}}") String url,
            @Value("${spring.flyway.user:${spring.datasource.username}}") String user,
            @Value("${spring.flyway.password:${spring.datasource.password}}") String password) {
        return DataSourceBuilder.create()
                .url(url).username(user).password(password).build();
    }

    /**
     * Flyway-DataSource mit SimpleDriverDataSource (kein HikariCP-Pool, kein initializationFailTimeout).
     * Umgeht FlywayAutoConfiguration.getMigrationDataSource() → kein DataSourceBuilder.deriveFrom()-Problem.
     * SimpleDriverDataSource öffnet direkte JDBC-Verbindungen via DriverManager — für Flyway ausreichend.
     */
    @Bean
    @FlywayDataSource
    public DataSource flywayDataSource(
            @Value("${spring.flyway.url}") String url,
            @Value("${spring.flyway.user}") String user,
            @Value("${spring.flyway.password}") String password) {
        return DataSourceBuilder.create()
                .type(SimpleDriverDataSource.class)
                .url(url)
                .username(user)
                .password(password)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Bean("adminJdbcTemplate")
    public JdbcTemplate adminJdbcTemplate(@Qualifier("adminDataSource") DataSource adminDataSource) {
        return new JdbcTemplate(adminDataSource);
    }
}
