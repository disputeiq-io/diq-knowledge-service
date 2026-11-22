package io.disputeiq.knowledge.infrastructure;

import io.disputeiq.platform.tenancy.TenantId;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Binds PostgreSQL transaction-local {@code app.tenant_id} for Row-Level Security enforcement. */
@Component
public class TenantRlsContext {

    private final boolean postgresBackend;

    public TenantRlsContext(DataSource dataSource) {
        this.postgresBackend = detectPostgres(dataSource);
    }

    public void apply(JdbcClient jdbcClient, TenantId tenantId) {
        if (!postgresBackend) {
            return;
        }
        jdbcClient
                .sql("SELECT set_config('app.tenant_id', :tenantId, true)")
                .param("tenantId", tenantId.toString())
                .query(String.class)
                .single();
    }

    private static boolean detectPostgres(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = connection.getMetaData().getDatabaseProductName();
            return product != null && product.toLowerCase().contains("postgresql");
        } catch (SQLException ex) {
            return false;
        }
    }
}
