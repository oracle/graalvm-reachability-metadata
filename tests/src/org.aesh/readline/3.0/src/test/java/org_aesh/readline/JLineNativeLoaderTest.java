/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.readline;

import org.jline.nativ.JLineNativeLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class JLineNativeLoaderTest {
    private static final String JLINE_TMPDIR_PROPERTY = "jline.tmpdir";
    private static final String JLINE_LIBRARY_PATH_PROPERTY = "library.jline.path";
    private static final String JLINE_LIBRARY_NAME_PROPERTY = "library.jline.name";

    @Test
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void initializeExtractsBundledNativeLibraryResource() throws Exception {
        String originalTmpdir = System.getProperty(JLINE_TMPDIR_PROPERTY);
        String originalLibraryPath = System.getProperty(JLINE_LIBRARY_PATH_PROPERTY);
        String originalLibraryName = System.getProperty(JLINE_LIBRARY_NAME_PROPERTY);
        Path nativeTempDirectory = Files.createTempDirectory("jline-native-loader-test");

        try {
            System.setProperty(JLINE_TMPDIR_PROPERTY, nativeTempDirectory.toString());
            System.clearProperty(JLINE_LIBRARY_PATH_PROPERTY);
            System.clearProperty(JLINE_LIBRARY_NAME_PROPERTY);

            assertThat(JLineNativeLoader.initialize()).isTrue();

            String nativeLibraryPath = JLineNativeLoader.getNativeLibraryPath();
            assertThat(nativeLibraryPath)
                    .isNotBlank()
                    .contains("jlinenative")
                    .satisfiesAnyOf(
                            path -> assertThat(path).contains(nativeTempDirectory.toString()),
                            path -> assertThat(path).startsWith(System.getProperty("java.io.tmpdir")));
            assertThat(JLineNativeLoader.getNativeLibrarySourceUrl())
                    .isNotBlank()
                    .contains("org/jline/nativ")
                    .contains("jlinenative");
        } finally {
            restoreProperty(JLINE_TMPDIR_PROPERTY, originalTmpdir);
            restoreProperty(JLINE_LIBRARY_PATH_PROPERTY, originalLibraryPath);
            restoreProperty(JLINE_LIBRARY_NAME_PROPERTY, originalLibraryName);
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
