/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.seata.common.io.FileLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileLoaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void loadResolvesDecodedAbsoluteFileSystemPath() throws IOException {
        Path targetFile = temporaryDirectory.resolve("file loader sample.txt");
        Files.writeString(targetFile, "loaded from file system");
        String encodedPath = targetFile.toString().replace(" ", "%20");

        File loadedFile = FileLoader.load(encodedPath);

        assertThat(loadedFile).isNotNull();
        assertThat(loadedFile.toPath()).isEqualTo(targetFile);
        assertThat(Files.readString(loadedFile.toPath())).isEqualTo("loaded from file system");
    }
}
