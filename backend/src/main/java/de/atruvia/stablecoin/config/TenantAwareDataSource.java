package de.atruvia.stablecoin.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * DataSource-Proxy, der bei jedem getConnection()-Aufruf app.current_tenant als
 * Session-Variable setzt (set_config, is_local=false).
 *
 * Session-Level (not transaction-local): Tenant bleibt für die Connection-Laufzeit gesetzt.
 * JwtAuthFilter.finally → TenantContext.clear() beendet den Request-Scope.
 * Bei Rückgabe der Connection an den Pool setzt der nächste getConnection()-Aufruf
 * den Tenant-Wert neu (da jeder Request über diesen Proxy geht).
 */
public class TenantAwareDataSource extends DelegatingDataSource {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareDataSource.class);
    private static final String SET_TENANT_SQL = "SELECT set_config('app.current_tenant', ?, false)";

    public TenantAwareDataSource(DataSource delegate) {
        super(delegate);
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection conn = super.getConnection();
        applyTenant(conn);
        return conn;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection conn = super.getConnection(username, password);
        applyTenant(conn);
        return conn;
    }

    private void applyTenant(Connection conn) throws SQLException {
        String tenantId = TenantContext.get();
        String value = tenantId != null ? tenantId : "";
        try (PreparedStatement ps = conn.prepareStatement(SET_TENANT_SQL)) {
            ps.setString(1, value);
            ps.execute();
        }
        log.trace("[TENANT-DS] app.current_tenant='{}' auf Connection gesetzt", value);
    }
}
