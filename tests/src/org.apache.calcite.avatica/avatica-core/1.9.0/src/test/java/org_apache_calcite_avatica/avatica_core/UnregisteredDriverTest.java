/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.BuiltInConnectionProperty;
import org.apache.calcite.avatica.remote.Driver;
import org.apache.calcite.avatica.remote.MockJsonService;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverPropertyInfo;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UnregisteredDriverTest {
    @Test
    void initializesJdbcFactoryAndExposesConnectionProperties() throws Exception {
        Driver driver = new Driver();
        assertTrue(driver.acceptsURL("jdbc:avatica:remote:"));
        assertFalse(driver.acceptsURL("jdbc:unrelated:"));
        Properties properties = new Properties();
        properties.setProperty("schema", "sales");
        properties.setProperty("factory", MockJsonService.Factory.class.getName());
        try (Connection connection = driver.connect("jdbc:avatica:remote:", properties)) {
            assertEquals(4, connection.getMetaData().getJDBCMajorVersion());
            assertTrue(connection.getMetaData().getJDBCMinorVersion() >= 1);
            assertFalse(connection.isClosed());
        }
        DriverPropertyInfo[] information = driver.getPropertyInfo("jdbc:avatica:remote:", properties);
        assertTrue(Arrays.stream(information).anyMatch(p -> "schema".equals(p.name) && "sales".equals(p.value)));
        assertTrue(Arrays.stream(information).anyMatch(p -> BuiltInConnectionProperty.HTTP_CLIENT_IMPL.name().equals(p.name)));
    }
}
