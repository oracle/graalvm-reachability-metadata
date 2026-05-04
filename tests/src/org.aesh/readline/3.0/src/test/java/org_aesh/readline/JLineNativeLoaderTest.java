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
    private static final String TEST_LIBRARY_NAME = "jlinenative-test.dll";
    private static final String TEST_RESOURCE_PATH = "org/jline/nativ/Windows/x86_64/" + TEST_LIBRARY_NAME;

    @Test
    void initializesNativeLoaderFromPackagedResource(@TempDir Path tempDir) {
        String originalOsName = System.getProperty("os.name");
        String originalOsArch = System.getProperty("os.arch");
        String originalLibraryName = System.getProperty("library.jline.name");
        String originalLibraryPath = System.getProperty("library.jline.path");
        String originalTmpDir = System.getProperty("jline.tmpdir");
        String originalJavaLibraryPath = System.getProperty("java.library.path");

        try {
            System.setProperty("os.name", "Windows 11");
            System.setProperty("os.arch", "x86_64");
            System.setProperty("library.jline.name", TEST_LIBRARY_NAME);
            System.clearProperty("library.jline.path");
            System.setProperty("jline.tmpdir", tempDir.toString());
            System.setProperty("java.library.path", "");

            assertThat(JLineNativeLoader.class.getResource("/" + TEST_RESOURCE_PATH)).isNotNull();
            assertThat(JLineNativeLoader.initialize()).isTrue();

            Path nativeLibrary = Path.of(JLineNativeLoader.getNativeLibraryPath());
            assertThat(nativeLibrary).exists();
            assertThat(nativeLibrary.getParent()).isEqualTo(tempDir);
            assertThat(nativeLibrary.getFileName().toString())
                    .startsWith("jlinenative-")
                    .endsWith(TEST_LIBRARY_NAME);
            assertThat(Files.isRegularFile(nativeLibrary)).isTrue();
            assertThat(Files.isReadable(nativeLibrary)).isTrue();

            assertThat(JLineNativeLoader.getNativeLibrarySourceUrl())
                    .isNotBlank()
                    .contains(TEST_RESOURCE_PATH);
            assertThat(JLineNativeLoader.initialize()).isTrue();
        } finally {
            restoreProperty("os.name", originalOsName);
            restoreProperty("os.arch", originalOsArch);
            restoreProperty("library.jline.name", originalLibraryName);
            restoreProperty("library.jline.path", originalLibraryPath);
            restoreProperty("jline.tmpdir", originalTmpDir);
            restoreProperty("java.library.path", originalJavaLibraryPath);
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
