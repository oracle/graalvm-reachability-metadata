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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoggerTest {
    @Test
    void configuresTheFileAccessProviderFromConnectionProperties() throws Exception {
        String key = "provider-" + UUID.randomUUID();
        String url = "jdbc:hsqldb:mem:" + UUID.randomUUID() + ";shutdown=true"
                + FileAccessTestSupport.configuration(key);
        try (Connection connection = DatabaseSupport.connect(url); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("VALUES (20 + 22)")) {
            assertTrue(result.next());
            assertEquals(42, result.getInt(1));
            assertEquals(key, FileAccessTestSupport.DiskAccess.lastKey);
        }
    }
}
