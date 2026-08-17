package de.atruvia.stablecoin.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Ersetzt die Spring-Boot-Auto-DataSource durch einen TenantAwareDataSource-Proxy.
 * Der Proxy setzt app.current_tenant auf jeder Connection bei Pool-Entnahme.
 *
 * Zwei Datasources:
 *  - hikariBaseDataSource: roher HikariCP-Pool (stablecoin_app, RLS-User)
 *  - dataSource (Primary): Tenant-Proxy-Wrapper → von JPA / Hibernate genutzt
 *
 * Flyway nutzt spring.flyway.url/user/password unabhängig (stablecoin, BYPASSRLS).
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
}
