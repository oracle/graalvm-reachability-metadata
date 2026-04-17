/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.seata.common.io.FileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FileLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadFallsBackToFileSystemPathWhenClasspathRootLookupRuns() throws IOException {
        Path filePath = Files.writeString(tempDir.resolve("seata-file-loader.txt"), "seata");

        File loadedFile = FileLoader.load(filePath.toString());

        assertThat(loadedFile).isEqualTo(filePath.toFile());
        assertThat(loadedFile).exists();
    }
}
