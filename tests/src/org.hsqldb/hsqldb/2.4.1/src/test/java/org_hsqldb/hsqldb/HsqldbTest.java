/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HsqldbTest {
    @Test
    void commitsAndRollsBackRows() throws Exception {
        try (Connection connection = DatabaseSupport.memory(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE messages (id INTEGER PRIMARY KEY, body VARCHAR(40))");
            connection.setAutoCommit(false);
            try (PreparedStatement insert = connection.prepareStatement("INSERT INTO messages VALUES (?, ?)")) {
                insert.setInt(1, 1);
                insert.setString(2, "committed");
                assertEquals(1, insert.executeUpdate());
                connection.commit();
                insert.setInt(1, 2);
                insert.setString(2, "rolled back");
                insert.executeUpdate();
                connection.rollback();
            }
            try (ResultSet result = statement.executeQuery("SELECT body FROM messages")) {
                assertTrue(result.next());
                assertEquals("committed", result.getString(1));
                assertFalse(result.next());
            }
        }
    }
}
