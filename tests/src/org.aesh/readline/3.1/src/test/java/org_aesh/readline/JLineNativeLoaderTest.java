/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.nativ.JLineNativeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JLineNativeLoaderTest {
    private static final String LIBRARY_PATH_PROPERTY = "library.jline.path";
    private static final String LIBRARY_NAME_PROPERTY = "library.jline.name";
    private static final String TEMP_DIRECTORY_PROPERTY = "jline.tmpdir";

    @TempDir
    Path temporaryDirectory;

    @Test
    void initializeExtractsBundledNativeLibraryResource() {
        String originalLibraryPath = System.getProperty(LIBRARY_PATH_PROPERTY);
        String originalLibraryName = System.getProperty(LIBRARY_NAME_PROPERTY);
        String originalTempDirectory = System.getProperty(TEMP_DIRECTORY_PROPERTY);

        try {
            System.clearProperty(LIBRARY_PATH_PROPERTY);
            System.clearProperty(LIBRARY_NAME_PROPERTY);
            System.setProperty(TEMP_DIRECTORY_PROPERTY, temporaryDirectory.toString());

            assertThat(JLineNativeLoader.initialize()).isTrue();
            assertThat(JLineNativeLoader.getNativeLibraryPath())
                    .isNotBlank()
                    .satisfies(path -> assertThat(Files.exists(Path.of(path))).isTrue());
            assertThat(JLineNativeLoader.getNativeLibrarySourceUrl()).isNotBlank();
        } finally {
            restoreProperty(LIBRARY_PATH_PROPERTY, originalLibraryPath);
            restoreProperty(LIBRARY_NAME_PROPERTY, originalLibraryName);
            restoreProperty(TEMP_DIRECTORY_PROPERTY, originalTempDirectory);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
