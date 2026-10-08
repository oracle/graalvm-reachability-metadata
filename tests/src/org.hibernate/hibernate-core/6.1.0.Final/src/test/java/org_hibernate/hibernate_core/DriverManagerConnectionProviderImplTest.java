/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DriverManagerConnectionProviderImplTest {

    @Test
    public void opensAndClosesConnectionsWithTheConfiguredJdbcDriver() throws Exception {
        DriverManagerConnectionProviderImpl provider = new DriverManagerConnectionProviderImpl();
        provider.configure(Map.of(
                "hibernate.connection.url", "jdbc:h2:mem:driver-manager-provider",
                "hibernate.connection.driver_class", "org.h2.Driver"
        ));
        try {
            Connection connection = provider.getConnection();
            try {
                assertThat(connection.isClosed()).isFalse();
            } finally {
                provider.closeConnection(connection);
            }
        } finally {
            provider.stop();
        }
    }
}
