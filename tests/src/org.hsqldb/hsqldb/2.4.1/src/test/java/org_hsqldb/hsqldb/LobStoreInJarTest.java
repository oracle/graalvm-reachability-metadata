/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.nio.charset.StandardCharsets;

import org.hsqldb.Database;
import org.hsqldb.DatabaseManager;
import org.hsqldb.DatabaseType;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.persist.LobStoreInJar;
import org.junit.jupiter.api.Test;

public class LobStoreInJarTest {
    private static final String DATABASE_NAME = "lobstoreinjarcoverage";
    private static final byte[] FIRST_BLOCK = "ABCD".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] SECOND_BLOCK = "WXYZ".getBytes(StandardCharsets.US_ASCII);

    @Test
    public void readsLobBlocksFromContextClassLoaderResource() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Database database = DatabaseManager.getDatabase(DatabaseType.DB_MEM.value(), DATABASE_NAME,
                new HsqlProperties());
        LobStoreInJar store = new LobStoreInJar(database, FIRST_BLOCK.length);

        try {
            Thread.currentThread().setContextClassLoader(LobStoreInJarTest.class.getClassLoader());

            assertArrayEquals(FIRST_BLOCK, store.getBlockBytes(0, 1));
            assertArrayEquals(SECOND_BLOCK, store.getBlockBytes(1, 1));
            assertArrayEquals(FIRST_BLOCK, store.getBlockBytes(0, 1));
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            store.close();
            database.close(Database.CLOSEMODE_IMMEDIATELY);
        }
    }
}
