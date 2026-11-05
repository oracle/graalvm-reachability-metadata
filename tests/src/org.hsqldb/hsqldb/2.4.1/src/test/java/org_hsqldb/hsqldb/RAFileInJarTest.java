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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RAFileInJarTest {
    @Test
    void queriesCachedRowsInAClasspathDatabase() throws Exception {
        try (Connection connection = DatabaseSupport.connect(
                "jdbc:hsqldb:res:/org_hsqldb/hsqldb/catalog;shutdown=true");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT name FROM products ORDER BY id")) {
            assertTrue(result.next());
            assertEquals("notebook", result.getString(1));
            assertTrue(result.next());
            assertEquals("pencil", result.getString(1));
            assertFalse(result.next());
        }
    }
}
