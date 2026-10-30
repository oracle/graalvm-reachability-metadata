/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_sql;

import groovy.sql.GroovyRowResult;
import groovy.sql.Sql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class SqlTest {
    @Test
    void loadsConfiguredDriverAndExecutesParameterizedQueries() throws Exception {
        try (Sql sql = Sql.newInstance("jdbc:h2:mem:sql_driver", "sa", "", "org.h2.Driver")) {
            assertFalse(sql.getConnection().isClosed());
            sql.execute("""
                    CREATE TABLE inventory (
                        id INTEGER PRIMARY KEY,
                        item_name VARCHAR(80) NOT NULL,
                        quantity INTEGER NOT NULL
                    )
                    """);
            assertEquals(1, sql.executeUpdate(
                    "INSERT INTO inventory VALUES (?, ?, ?)", List.of(1, "notebook", 3)));
            assertEquals(1, sql.executeUpdate(
                    "UPDATE inventory SET quantity = ? WHERE id = ?", List.of(5, 1)));

            GroovyRowResult row = sql.firstRow(
                    "SELECT item_name, quantity FROM inventory WHERE id = ?", List.of(1));
            assertEquals("notebook", row.get("ITEM_NAME"));
            assertEquals(5, row.get("QUANTITY"));
            assertEquals(1, sql.executeUpdate("DELETE FROM inventory WHERE id = ?", List.of(1)));
            assertTrue(sql.rows("SELECT * FROM inventory").isEmpty());
        }
    }

    @Test
    void rejectsUnknownDriverAfterTryingAvailableClassLoaders() {
        String driverName = "org.example.jdbc.UnknownDriver";
        ClassNotFoundException exception = assertThrows(ClassNotFoundException.class,
                () -> Sql.newInstance("jdbc:h2:mem:unknown_driver", driverName));
        assertEquals(driverName, exception.getMessage());
    }
}
