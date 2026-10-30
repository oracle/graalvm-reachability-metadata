/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import org.hsqldb.lib.RCData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RCDataTest {
    @Test
    void opensAConnectionUsingConfiguredCredentials() throws Exception {
        String trustStore = System.getProperty("javax.net.ssl.trustStore");
        try {
            RCData settings = new RCData("local", "jdbc:hsqldb:mem:" + UUID.randomUUID() + ";shutdown=true",
                    "SA", "", "org.hsqldb.jdbc.JDBCDriver", null, null);
            try (Connection connection = settings.getConnection(); Statement statement = connection.createStatement();
                    ResultSet result = statement.executeQuery("VALUES CURRENT_USER")) {
                assertTrue(result.next());
                assertEquals("SA", result.getString(1));
            }
        } finally {
            if (trustStore == null) {
                System.clearProperty("javax.net.ssl.trustStore");
            } else {
                System.setProperty("javax.net.ssl.trustStore", trustStore);
            }
        }
    }
}
