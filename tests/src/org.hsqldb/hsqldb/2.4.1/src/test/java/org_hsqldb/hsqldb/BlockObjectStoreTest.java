/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class BlockObjectStoreTest {
    @TempDir
    Path directory;

    @Test
    void restoresCachedRowsWithPersistentSpaceDirectories() throws Exception {
        String url = "jdbc:hsqldb:file:" + directory.resolve("block-store") + ";shutdown=true";
        FileDatabaseSupport.assertPersistence(url, "SET FILES SPACE TRUE");
    }
}
