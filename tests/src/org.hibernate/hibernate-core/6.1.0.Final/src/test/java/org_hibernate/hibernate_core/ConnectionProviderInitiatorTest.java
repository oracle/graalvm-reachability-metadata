/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.BootstrapServiceRegistry;
import org.hibernate.boot.registry.BootstrapServiceRegistryBuilder;
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
    public void selectsC3p0ProviderWhenC3p0ConfigurationIsPresent() {
        assertProviderSelected(
                AvailableSettings.C3P0_CONFIG_PREFIX + ".min_size",
                C3p0ConnectionProvider.class
        );
    }

    @Test
    public void selectsProxoolProviderWhenProxoolConfigurationIsPresent() {
        assertProviderSelected(
                AvailableSettings.PROXOOL_CONFIG_PREFIX + ".pool_alias",
                ProxoolConnectionProvider.class
        );
    }

    @Test
    public void selectsHikariProviderWhenHikariConfigurationIsPresent() {
        assertProviderSelected("hibernate.hikari.maximumPoolSize", HikariConnectionProvider.class);
    }

    @Test
    public void selectsViburProviderWhenViburConfigurationIsPresent() {
        assertProviderSelected("hibernate.vibur.poolMaxSize", ViburConnectionProvider.class);
    }

    @Test
    public void selectsAgroalProviderWhenAgroalConfigurationIsPresent() {
        assertProviderSelected("hibernate.agroal.maxSize", AgroalConnectionProvider.class);
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

    private void assertProviderSelected(
            String configurationName,
            Class<? extends ConnectionProvider> expectedProviderType
    ) {
        BootstrapServiceRegistry bootstrapRegistry = new BootstrapServiceRegistryBuilder()
                .applyStrategySelector(
                        ConnectionProvider.class,
                        ConnectionProviderInitiator.C3P0_STRATEGY,
                        C3p0ConnectionProvider.class
                )
                .applyStrategySelector(
                        ConnectionProvider.class,
                        ConnectionProviderInitiator.PROXOOL_STRATEGY,
                        ProxoolConnectionProvider.class
                )
                .applyStrategySelector(
                        ConnectionProvider.class,
                        ConnectionProviderInitiator.HIKARI_STRATEGY,
                        HikariConnectionProvider.class
                )
                .applyStrategySelector(
                        ConnectionProvider.class,
                        ConnectionProviderInitiator.VIBUR_STRATEGY,
                        ViburConnectionProvider.class
                )
                .applyStrategySelector(
                        ConnectionProvider.class,
                        ConnectionProviderInitiator.AGROAL_STRATEGY,
                        AgroalConnectionProvider.class
                )
                .build();
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder(bootstrapRegistry)
                .applySetting(configurationName, "configured")
                .build();
        try {
            ConnectionProvider provider = registry.getService(ConnectionProvider.class);

            assertThat(provider).isExactlyInstanceOf(expectedProviderType);
            assertThat(provider.isUnwrappableAs(expectedProviderType)).isTrue();
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

    public static class C3p0ConnectionProvider extends ConfigurableConnectionProvider {
    }

    public static class ProxoolConnectionProvider extends ConfigurableConnectionProvider {
    }

    public static class HikariConnectionProvider extends ConfigurableConnectionProvider {
    }

    public static class ViburConnectionProvider extends ConfigurableConnectionProvider {
    }

    public static class AgroalConnectionProvider extends ConfigurableConnectionProvider {
    }
}
