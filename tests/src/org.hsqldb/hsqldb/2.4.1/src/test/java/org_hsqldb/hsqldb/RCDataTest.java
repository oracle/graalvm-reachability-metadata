/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.hsqldb.lib.RCData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class RCDataTest {
    private static final String DRIVER_PROPERTY = "org_hsqldb_hsqldb.rcdata.driver";
    private static final String TRUST_STORE_PROPERTY = "javax.net.ssl.trustStore";

    @Test
    @Timeout(30)
    void getConnectionLoadsDefaultJdbcDriverAndOpensConnection() throws Exception {
        String previousTrustStore = System.getProperty(TRUST_STORE_PROPERTY);
        RCData rcData = inMemoryRCData("default_driver");

        try (Connection connection = rcData.getConnection()) {
            assertRoundTripQuery(connection);
        } finally {
            restoreSystemProperty(TRUST_STORE_PROPERTY, previousTrustStore);
        }
    }

    @Test
    @Timeout(30)
    void getConnectionLoadsConfiguredJdbcDriverAndOpensConnection() throws Exception {
        String previousDriverProperty = System.getProperty(DRIVER_PROPERTY);
        String previousTrustStore = System.getProperty(TRUST_STORE_PROPERTY);
        RCData rcData = inMemoryRCData("configured_driver");

        System.setProperty(DRIVER_PROPERTY, RCData.DEFAULT_JDBC_DRIVER);

        try (Connection connection = rcData.getConnection("${" + DRIVER_PROPERTY + "}", null)) {
            assertRoundTripQuery(connection);
        } finally {
            restoreSystemProperty(DRIVER_PROPERTY, previousDriverProperty);
            restoreSystemProperty(TRUST_STORE_PROPERTY, previousTrustStore);
        }
    }

    private static RCData inMemoryRCData(String prefix) throws Exception {
        String databaseName = "rcdata_" + prefix + "_" + Long.toUnsignedString(System.nanoTime());
        String url = "jdbc:hsqldb:mem:" + databaseName + ";shutdown=true";

        return new RCData("memory", url, "SA", "", null, null, null, null, null);
    }

    private static void assertRoundTripQuery(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE sample(value INTEGER)");
            statement.execute("INSERT INTO sample(value) VALUES (42)");

            try (ResultSet resultSet = statement.executeQuery("SELECT value FROM sample")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(42);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    private static void restoreSystemProperty(String propertyName, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(propertyName);
        } else {
            System.setProperty(propertyName, previousValue);
        }
    }
}
