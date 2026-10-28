/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_geolatte.geolatte_geom;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.OracleConnection;
import oracle.jdbc.OracleConnectionWrapper;
import oracle.jdbc.driver.T2CConnection;
import org.geolatte.geom.codec.db.oracle.DefaultConnectionFinder;
import org.junit.jupiter.api.Test;

public class DefaultConnectionFinderTest {
    @Test
    void retrievesPhysicalConnectionFromNestedJdbcWrappers() throws SQLException {
        try (UnconnectedOracleConnection physical = new UnconnectedOracleConnection()) {
            OracleConnectionWrapper inner = new OracleConnectionWrapper(physical);
            OracleConnectionWrapper outer = new OracleConnectionWrapper(inner);
            DefaultConnectionFinder finder = new DefaultConnectionFinder();

            Connection found = finder.find(outer);

            assertThat(found).isSameAs(physical);
            assertThat(finder.find(inner)).isSameAs(physical);
            assertThat(finder.find(physical)).isSameAs(physical);
            assertThat(((OracleConnection) found).getDefaultRowPrefetch()).isEqualTo(37);
        }
    }

    // Construction configures the driver; no logon or OCI operation is needed for unwrapping.
    private static final class UnconnectedOracleConnection extends T2CConnection {
        private UnconnectedOracleConnection() throws SQLException {
            super("jdbc:oracle:oci:@", connectionProperties(), null);
        }

        private static Properties connectionProperties() {
            Properties properties = new Properties();
            properties.setProperty(OracleConnection.CONNECTION_PROPERTY_DEFAULT_ROW_PREFETCH, "37");
            return properties;
        }
    }
}
