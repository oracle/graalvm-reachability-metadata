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

import java.io.Writer;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC character large-object subsystem for a stored document.
public class CharacterDocumentClobTest {
    @Test
    @Timeout(60)
    public void storesAndReadsACharacterDocumentThroughJdbcClob() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:character_document_clob_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");
        String document = "{" + "\"title\":\"Release notes\","
                + "\"body\":\"A longer document remains intact.\"}";

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE documents (id INTEGER PRIMARY KEY, content CLOB)");

            Clob uploaded = connection.createClob();
            try {
                try (Writer writer = uploaded.setCharacterStream(1)) {
                    writer.write(document);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO documents VALUES (?, ?)")) {
                    insert.setInt(1, 31);
                    insert.setClob(2, uploaded);
                    assertEquals(1, insert.executeUpdate());
                }
            } finally {
                uploaded.free();
            }
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT content FROM documents WHERE id = ?")) {
            query.setInt(1, 31);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                Clob downloaded = resultSet.getClob(1);
                try {
                    assertEquals(document.length(), downloaded.length());
                    assertEquals(document, downloaded.getSubString(1, (int) downloaded.length()));
                } finally {
                    downloaded.free();
                }
                assertTrue(!resultSet.next());
            }
        }
    }
}
