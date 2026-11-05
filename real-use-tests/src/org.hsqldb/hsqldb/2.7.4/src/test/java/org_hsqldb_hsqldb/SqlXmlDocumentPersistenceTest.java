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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLXML;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC SQLXML subsystem for storing an application document.
public class SqlXmlDocumentPersistenceTest {
    @Test
    @Timeout(60)
    public void storesAndReadsAnXmlDocumentThroughJdbcSqlxml() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:sqlxml_document_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE documents (id INTEGER PRIMARY KEY, payload VARCHAR(1000))");

            SQLXML document = connection.createSQLXML();
            document.setString("<order><item sku=\"PENCIL\" quantity=\"3\"/></order>");
            String serializedDocument = document.getString();
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO documents VALUES (?, ?)")) {
                insert.setInt(1, 17);
                insert.setString(2, serializedDocument);
                assertEquals(1, insert.executeUpdate());
            } finally {
                document.free();
            }
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT payload FROM documents WHERE id = ?")) {
            query.setInt(1, 17);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                SQLXML document = connection.createSQLXML();
                try {
                    document.setString(resultSet.getString(1));
                    String xml = document.getString();
                    assertTrue(xml.contains("<order>"));
                    assertTrue(xml.contains("sku=\"PENCIL\""));
                    assertTrue(xml.contains("quantity=\"3\""));
                } finally {
                    document.free();
                }
                assertTrue(!resultSet.next());
            }
        }
    }
}
