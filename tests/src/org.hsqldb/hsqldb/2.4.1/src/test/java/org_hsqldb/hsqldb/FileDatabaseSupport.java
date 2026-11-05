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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FileDatabaseSupport {
    private FileDatabaseSupport() {
    }

    static void assertPersistence(String url, String configuration) throws Exception {
        try (Connection connection = DatabaseSupport.connect(url); Statement statement = connection.createStatement()) {
            if (configuration != null) {
                statement.execute(configuration);
            }
            statement.execute("CREATE CACHED TABLE records (id INTEGER PRIMARY KEY, payload VARCHAR(80))");
            statement.executeUpdate("INSERT INTO records VALUES (1, 'first'), (2, 'second'), (3, 'third')");
            statement.execute("CHECKPOINT");
        }
        try (Connection connection = DatabaseSupport.connect(url); Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT payload FROM records ORDER BY id")) {
            for (String expected : new String[]{"first", "second", "third"}) {
                assertTrue(result.next());
                assertEquals(expected, result.getString(1));
            }
            assertFalse(result.next());
        }
    }
}
