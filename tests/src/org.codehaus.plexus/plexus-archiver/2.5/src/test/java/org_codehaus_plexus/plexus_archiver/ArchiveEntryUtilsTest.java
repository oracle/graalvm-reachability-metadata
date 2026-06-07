/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_archiver;

import java.io.File;
import java.nio.file.Path;

import org.codehaus.plexus.archiver.util.ArchiveEntryUtils;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class ArchiveEntryUtilsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void chmodWithJvmPermissionsAppliesAllFilePermissionMethods() throws Exception {
        File file = temporaryDirectory.resolve("archive-entry-utils-permissions.txt").toFile();
        assertThat(file.createNewFile()).isTrue();

        boolean originalJvmFilePermAvailable = ArchiveEntryUtils.jvmFilePermAvailable;
        ArchiveEntryUtils.jvmFilePermAvailable = true;
        try {
            Logger logger = new ConsoleLogger(Logger.LEVEL_DISABLED, "archive-entry-utils-test");
            int ownerReadWriteExecuteMode = 0700;

            ArchiveEntryUtils.chmod(file, ownerReadWriteExecuteMode, logger, true);

            assertThat(file)
                .isFile()
                .canRead()
                .canWrite();
            if (Os.isFamily(Os.FAMILY_UNIX)) {
                assertThat(file.canExecute()).isTrue();
            }
        } finally {
            ArchiveEntryUtils.jvmFilePermAvailable = originalJvmFilePermAvailable;
        }
    }
}
