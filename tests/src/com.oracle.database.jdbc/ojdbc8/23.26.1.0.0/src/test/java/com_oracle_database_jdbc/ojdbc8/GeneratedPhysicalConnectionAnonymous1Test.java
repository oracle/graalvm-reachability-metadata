/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.Properties;
import oracle.jdbc.driver.T2CConnection;
import org.junit.jupiter.api.Test;

public class GeneratedPhysicalConnectionAnonymous1Test {
    @Test
    void initializesGeneratedConnectionPropertiesForAnApplication() throws Exception {
        T2CConnection connection = new DetachedT2CConnection();

        assertThat(connection.getProviderAllowedProperties())
                .contains("password", "URL");
    }

    private static final class DetachedT2CConnection extends T2CConnection {
        private DetachedT2CConnection() throws SQLException {
            super("jdbc:oracle:oci:@", new Properties(), null);
        }
    }
}
