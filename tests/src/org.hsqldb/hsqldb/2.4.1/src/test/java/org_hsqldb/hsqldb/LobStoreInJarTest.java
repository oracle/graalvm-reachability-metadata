/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.sql.Blob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LobStoreInJarTest {
    @Test
    void readsABlobFromAClasspathDatabase() throws Exception {
        try (Connection connection = DatabaseSupport.connect(
                "jdbc:hsqldb:res:/org_hsqldb/hsqldb/catalog;shutdown=true");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT payload FROM products WHERE id = 1")) {
            assertTrue(result.next());
            Blob blob = result.getBlob(1);
            try {
                assertArrayEquals(new byte[]{0, 1, 2, 127, -1}, blob.getBytes(1, (int) blob.length()));
                assertArrayEquals(new byte[]{1, 2}, blob.getBytes(2, 2));
            } finally {
                blob.free();
            }
        }
    }
}
