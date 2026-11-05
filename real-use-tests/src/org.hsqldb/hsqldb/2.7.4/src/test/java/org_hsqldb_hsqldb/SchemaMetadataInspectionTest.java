/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb_hsqldb;

import org.hsqldb.jdbc.JDBCDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC schema-metadata subsystem for an application startup check.
public class SchemaMetadataInspectionTest {
    @Test
    @Timeout(60)
    public void inspectsTheSchemaBeforeUsingAConfiguredTable() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:schema_metadata_inspection_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE customers ("
                    + "id INTEGER PRIMARY KEY, name VARCHAR(80), active BOOLEAN)");
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            assertEquals("HSQL Database Engine", metadata.getDatabaseProductName());
            assertTrue(metadata.supportsTransactions());

            try (ResultSet tables = metadata.getTables(
                    null, "PUBLIC", "CUSTOMERS", new String[] {"TABLE"})) {
                assertTrue(tables.next());
                assertEquals("CUSTOMERS", tables.getString("TABLE_NAME"));
                assertEquals("TABLE", tables.getString("TABLE_TYPE"));
                assertTrue(!tables.next());
            }

            List<String> columns = new ArrayList<>();
            try (ResultSet columnRows = metadata.getColumns(null, "PUBLIC", "CUSTOMERS", null)) {
                while (columnRows.next()) {
                    columns.add(columnRows.getString("COLUMN_NAME")
                            + ":" + columnRows.getString("TYPE_NAME"));
                }
            }
            assertEquals(List.of("ID:INTEGER", "NAME:VARCHAR", "ACTIVE:BOOLEAN"), columns);
        }
    }
}
