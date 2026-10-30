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

public class RAFileNIOTest {
    @TempDir
    Path directory;

    @Test
    void reopensCachedRowsFromAMemoryMappedDataFile() throws Exception {
        String url = "jdbc:hsqldb:file:" + directory.resolve("nio-store") + ";shutdown=true;hsqldb.nio_data_file=true";
        FileDatabaseSupport.assertPersistence(url, "SET FILES NIO TRUE");
    }
}
