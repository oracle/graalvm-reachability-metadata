/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jdbi_jdbi3_core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Jdbi's pooled data-source subsystem for serving application catalog queries.
public class PooledDataSourceIntegrationTest {
    @Test
    void servesCatalogQueriesThroughAnApplicationConnectionPool() {
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:h2:mem:pooled_catalog;DB_CLOSE_DELAY=-1");
        poolConfig.setMaximumPoolSize(2);
        poolConfig.setMinimumIdle(1);
        poolConfig.setConnectionTimeout(10_000);

        try (HikariDataSource dataSource = new HikariDataSource(poolConfig)) {
            Jdbi jdbi = Jdbi.create(dataSource);
            jdbi.useHandle(handle -> {
                handle.execute("""
                        CREATE TABLE catalog_entry (
                            sku VARCHAR(32) PRIMARY KEY,
                            name VARCHAR(64) NOT NULL,
                            available BOOLEAN NOT NULL
                        )
                        """);
                handle.execute("""
                        INSERT INTO catalog_entry (sku, name, available)
                        VALUES ('KB-01', 'Keyboard', TRUE),
                               ('MS-02', 'Mouse', FALSE)
                        """);
            });

            List<String> availableItems = jdbi.withHandle(handle -> handle.createQuery("""
                    SELECT name
                    FROM catalog_entry
                    WHERE available = TRUE
                    ORDER BY sku
                    """)
                    .mapTo(String.class)
                    .list());

            assertEquals(List.of("Keyboard"), availableItems);
        }
    }
}
