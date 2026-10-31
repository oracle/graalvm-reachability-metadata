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
import java.util.Locale;

import org.hsqldb.Trigger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TriggerDefTest {
    @Test
    void appliesAJavaBeforeInsertTrigger() throws Exception {
        try (Connection connection = DatabaseSupport.memory(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE contacts (id INTEGER PRIMARY KEY, name VARCHAR(40))");
            statement.execute("""
                    CREATE TRIGGER normalize_contact BEFORE INSERT ON contacts
                    FOR EACH ROW CALL "org_hsqldb.hsqldb.TriggerDefTest$NormalizeName"
                    """);
            assertEquals(1, statement.executeUpdate("INSERT INTO contacts VALUES (1, 'Alice')"));
            try (ResultSet result = statement.executeQuery("SELECT name FROM contacts WHERE id = 1")) {
                assertTrue(result.next());
                assertEquals("ALICE", result.getString(1));
            }
        }
    }

    public static class NormalizeName implements Trigger {
        @Override
        public void fire(int type, String triggerName, String tableName, Object[] oldRow, Object[] newRow) {
            newRow[1] = ((String) newRow[1]).toUpperCase(Locale.ROOT);
        }
    }
}
