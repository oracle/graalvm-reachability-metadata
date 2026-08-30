/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.jdbc.connections.internal.MultiTenantConnectionProviderInitiator;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MultiTenantConnectionProviderInitiatorTest {

    @Test
    public void instantiatesAConfiguredMultiTenantConnectionProvider() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            MultiTenantConnectionProvider provider = MultiTenantConnectionProviderInitiator.INSTANCE
                    .initiateService(
                            Map.of(
                                    AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER,
                                    ConfiguredProvider.class
                            ),
                            (ServiceRegistryImplementor) registry
                    );

            assertThat(provider).isExactlyInstanceOf(ConfiguredProvider.class);
            assertThat(provider.isUnwrappableAs(ConfiguredProvider.class)).isTrue();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class ConfiguredProvider implements MultiTenantConnectionProvider {
        private static final long serialVersionUID = 1L;

        @Override
        public Connection getAnyConnection() throws SQLException {
            throw new SQLException("No connection is needed to select the provider");
        }

        @Override
        public void releaseAnyConnection(Connection connection) throws SQLException {
            connection.close();
        }

        @Override
        public Connection getConnection(String tenantIdentifier) throws SQLException {
            throw new SQLException("No connection is needed to select the provider");
        }

        @Override
        public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
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
