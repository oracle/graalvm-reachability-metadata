/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabaseInformationTest {
    @Test
    void exposesUserTableColumnsAndPrimaryKeys() throws Exception {
        try (Connection connection = DatabaseSupport.memory(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE inventory (id INTEGER PRIMARY KEY, label VARCHAR(40) NOT NULL)");
            DatabaseMetaData metadata = connection.getMetaData();
            try (ResultSet columns = metadata.getColumns(null, "PUBLIC", "INVENTORY", "%")) {
                assertTrue(columns.next());
                assertEquals("ID", columns.getString("COLUMN_NAME"));
                assertEquals(Types.INTEGER, columns.getInt("DATA_TYPE"));
                assertTrue(columns.next());
                assertEquals("LABEL", columns.getString("COLUMN_NAME"));
                assertEquals(DatabaseMetaData.columnNoNulls, columns.getInt("NULLABLE"));
                assertFalse(columns.next());
            }
            try (ResultSet keys = metadata.getPrimaryKeys(null, "PUBLIC", "INVENTORY")) {
                assertTrue(keys.next());
                assertEquals("ID", keys.getString("COLUMN_NAME"));
                assertFalse(keys.next());
            }
        }
    }
}
