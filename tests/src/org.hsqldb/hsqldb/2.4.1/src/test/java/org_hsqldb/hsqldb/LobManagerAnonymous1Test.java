/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LobManagerAnonymous1Test {
    @Test
    void storesAndReadsBinaryAndCharacterLargeObjects() throws Exception {
        byte[] payload = {0, 1, 2, 3, 127, -1};
        try (Connection connection = DatabaseSupport.memory(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE documents (id INTEGER PRIMARY KEY, binary_data BLOB, text_data CLOB)");
            Blob blob = connection.createBlob();
            Clob clob = connection.createClob();
            try {
                blob.setBytes(1, payload);
                clob.setString(1, "large text payload");
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO documents VALUES (1, ?, ?)")) {
                    insert.setBlob(1, blob);
                    insert.setClob(2, clob);
                    assertEquals(1, insert.executeUpdate());
                }
                try (ResultSet result = statement.executeQuery("SELECT binary_data, text_data FROM documents")) {
                    assertTrue(result.next());
                    Blob storedBlob = result.getBlob(1);
                    Clob storedClob = result.getClob(2);
                    try {
                        assertArrayEquals(payload, storedBlob.getBytes(1, (int) storedBlob.length()));
                        assertEquals("large text payload", storedClob.getSubString(1, (int) storedClob.length()));
                    } finally {
                        storedBlob.free();
                        storedClob.free();
                    }
                }
            } finally {
                blob.free();
                clob.free();
            }
        }
    }
}
