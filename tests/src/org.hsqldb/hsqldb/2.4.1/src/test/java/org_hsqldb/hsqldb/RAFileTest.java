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

import static org.junit.jupiter.api.Assertions.assertTrue;

public class RAFileTest {
    @TempDir
    Path directory;

    @Test
    void persistsCachedRowsThroughAConfiguredStorageProvider() throws Exception {
        int previousOpens = FileAccessTestSupport.DiskStorage.opens.get();
        String url = "jdbc:hsqldb:file:" + directory.resolve("custom-store") + ";shutdown=true"
                + FileAccessTestSupport.configuration("disk");
        FileDatabaseSupport.assertPersistence(url, null);
        assertTrue(FileAccessTestSupport.DiskStorage.opens.get() >= previousOpens + 2);
    }
}
