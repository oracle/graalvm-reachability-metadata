/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlConnectOptions;
import org.hibernate.reactive.pool.impl.SqlClientPoolConfiguration;
import org.hibernate.reactive.provider.ReactiveServiceRegistryBuilder;
import org.hibernate.reactive.provider.Settings;
import org.hibernate.service.ServiceRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlClientPoolConfigurationInitiatorTest implements SqlClientPoolConfiguration {

    private static final int CONFIGURED_MAX_POOL_SIZE = 3;

    @Test
    void instantiatesConfiguredSqlClientPoolConfigurationClass() {
        Map<String, Object> configurationValues = new HashMap<>();
        configurationValues.put(Settings.SQL_CLIENT_POOL_CONFIG, SqlClientPoolConfigurationInitiatorTest.class.getName());

        ServiceRegistry serviceRegistry = new ReactiveServiceRegistryBuilder()
                .applySettings(configurationValues)
                .build();
        try {
            SqlClientPoolConfiguration configuration = serviceRegistry.getService(SqlClientPoolConfiguration.class);

            assertThat(configuration).isInstanceOf(SqlClientPoolConfigurationInitiatorTest.class);
            assertThat(configuration.poolOptions().getMaxSize()).isEqualTo(CONFIGURED_MAX_POOL_SIZE);
        } finally {
            ReactiveServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }

    @Override
    public PoolOptions poolOptions() {
        return new PoolOptions().setMaxSize(CONFIGURED_MAX_POOL_SIZE);
    }

    @Override
    public SqlConnectOptions connectOptions(URI uri) {
        return null;
    }
}
