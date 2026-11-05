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

import java.io.OutputStream;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises HSQLDB's JDBC large-object subsystem for storing a binary attachment.
public class BinaryAttachmentLobTest {
    @Test
    @Timeout(60)
    public void streamsAnAttachmentThroughJdbcBlob() throws Exception {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setDatabase("mem:binary_attachment_lob_scenario");
        dataSource.setUser("SA");
        dataSource.setPassword("");
        byte[] attachment = new byte[] {0, 7, 42, -1, 64, 100};

        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE attachments (id INTEGER PRIMARY KEY, content BLOB)");

            Blob uploaded = connection.createBlob();
            try {
                try (OutputStream output = uploaded.setBinaryStream(1)) {
                    output.write(attachment);
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO attachments VALUES (?, ?)")) {
                    insert.setInt(1, 23);
                    insert.setBlob(2, uploaded);
                    assertEquals(1, insert.executeUpdate());
                }
            } finally {
                uploaded.free();
            }
        }

        try (Connection connection = dataSource.getConnection();
                PreparedStatement query = connection.prepareStatement(
                        "SELECT content FROM attachments WHERE id = ?")) {
            query.setInt(1, 23);
            try (ResultSet resultSet = query.executeQuery()) {
                assertTrue(resultSet.next());
                Blob downloaded = resultSet.getBlob(1);
                try {
                    assertEquals(attachment.length, downloaded.length());
                    assertArrayEquals(attachment, downloaded.getBytes(1, attachment.length));
                } finally {
                    downloaded.free();
                }
                assertTrue(!resultSet.next());
            }
        }
    }
}
