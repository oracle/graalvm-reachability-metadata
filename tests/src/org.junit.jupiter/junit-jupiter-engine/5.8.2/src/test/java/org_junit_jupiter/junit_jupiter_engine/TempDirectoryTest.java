/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class TempDirectoryTest {
    @TempDir
    Path pathTempDirectory;

    @TempDir
    File fileTempDirectory;

    @Test
    void injectsTempDirAnnotatedFields() throws Exception {
        assertThat(pathTempDirectory).isNotNull();
        assertThat(Files.isDirectory(pathTempDirectory)).isTrue();

        assertThat(fileTempDirectory).isNotNull();
        assertThat(fileTempDirectory).isDirectory();

        Path file = pathTempDirectory.resolve("created-by-field-injected-temp-dir.txt");
        Files.writeString(file, "created through injected Path");

        assertThat(file).hasContent("created through injected Path");
        assertThat(fileTempDirectory.toPath()).isDirectory();
    }
}
