/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnectOptions;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.reactive.pool.impl.DefaultSqlClientPoolConfiguration;
import org.hibernate.reactive.pool.impl.SqlClientPoolConfiguration;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SqlClientPoolConfigurationInitiatorTest {
    @Test
    void loadsAndConfiguresPoolStrategyByClassName() {
        StandardServiceRegistry registry = new ReactiveServiceRegistryBuilder()
                .applySetting(Settings.SQL_CLIENT_POOL_CONFIG, DefaultSqlClientPoolConfiguration.class.getName())
                .applySetting(Settings.POOL_SIZE, 3)
                .applySetting(Settings.POOL_MAX_WAIT_QUEUE_SIZE, 12)
                .applySetting(Settings.USER, "reader")
                .applySetting(Settings.PASS, "password")
                .build();
        try {
            SqlClientPoolConfiguration configuration = registry.getService(SqlClientPoolConfiguration.class);
            PoolOptions poolOptions = configuration.poolOptions();
            assertEquals(3, poolOptions.getMaxSize());
            assertEquals(12, poolOptions.getMaxWaitQueueSize());
            SqlConnectOptions connectOptions = configuration.connectOptions(URI.create("postgresql://localhost:5432/catalog"));
            assertEquals("catalog", connectOptions.getDatabase());
            assertEquals("reader", connectOptions.getUser());
            assertEquals("password", connectOptions.getPassword());
            assertEquals(5432, connectOptions.getPort());
        } finally {
            ReactiveServiceRegistryBuilder.destroy(registry);
        }
    }
}
