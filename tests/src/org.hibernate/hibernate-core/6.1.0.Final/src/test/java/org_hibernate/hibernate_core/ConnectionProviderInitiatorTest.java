/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.registry.selector.spi.StrategySelector;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.internal.ConnectionProviderInitiator;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ConnectionProviderInitiatorTest {

    @Test
    public void instantiatesAnExplicitConnectionProvider() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        Map<String, Object> settings = Map.of(
                AvailableSettings.CONNECTION_PROVIDER,
                ConfigurableConnectionProvider.class
        );
        try {
            ConnectionProvider provider = new ConnectionProviderInitiator().initiateService(
                    settings,
                    (ServiceRegistryImplementor) registry
            );

            assertThat(provider).isInstanceOf(ConfigurableConnectionProvider.class);
            assertThat(provider.isUnwrappableAs(ConfigurableConnectionProvider.class)).isTrue();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    @Test
    public void instantiatesTheOnlyRegisteredConnectionProviderStrategy() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            registry.getService(StrategySelector.class).registerStrategyImplementor(
                    ConnectionProvider.class,
                    "registered-provider",
                    ConfigurableConnectionProvider.class
            );

            ConnectionProvider provider = new ConnectionProviderInitiator().initiateService(
                    Map.of(
                            ConnectionProviderInitiator.INJECTION_DATA,
                            Map.of("label", "configured")
                    ),
                    (ServiceRegistryImplementor) registry
            );

            assertThat(provider).isInstanceOf(ConfigurableConnectionProvider.class);
            assertThat(((ConfigurableConnectionProvider) provider).getLabel()).isEqualTo("configured");
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class ConfigurableConnectionProvider implements ConnectionProvider {
        private String label;

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("No connection was requested by this service test");
        }

        @Override
        public void closeConnection(Connection connection) throws SQLException {
            connection.close();
        }

        @Override
        public boolean supportsAggressiveRelease() {
            return false;
        }

        @Override
        public boolean isUnwrappableAs(Class<?> unwrapType) {
            return unwrapType.isInstance(this);
        }

        @Override
        public <T> T unwrap(Class<T> unwrapType) {
            if (isUnwrappableAs(unwrapType)) {
                return unwrapType.cast(this);
            }
            throw new UnknownUnwrapTypeException(unwrapType);
        }
    }
}
