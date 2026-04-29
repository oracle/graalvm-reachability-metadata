/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_fusesource_jansi.jansi;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.fusesource.jansi.internal.JansiLoader;
import org.fusesource.jansi.internal.OSInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class LibraryTest {

    @Test
    void initializeExtractsAndLoadsBundledNativeLibrary(@TempDir final Path tempDirectory) throws IOException {
        Path extractionDirectory = tempDirectory.resolve("jansi-extraction");
        Files.createDirectories(extractionDirectory);
        String mappedLibraryName = mapLibraryName("jansi");
        String nativeResourcePath = "/org/fusesource/jansi/internal/native/"
                + OSInfo.getNativeLibFolderPathForCurrentOS() + "/" + mappedLibraryName;
        URL nativeResource = JansiLoader.class.getResource(nativeResourcePath);

        assertThat(nativeResource).isNotNull();

        String previousTempDirectory = setSystemProperty("jansi.tmpdir", extractionDirectory.toString());
        String previousLibraryPath = clearSystemProperty("library.jansi.path");
        String previousLibraryName = clearSystemProperty("library.jansi.name");
        try {
            assertThat(JansiLoader.initialize()).isTrue();
        } finally {
            restoreSystemProperty("jansi.tmpdir", previousTempDirectory);
            restoreSystemProperty("library.jansi.path", previousLibraryPath);
            restoreSystemProperty("library.jansi.name", previousLibraryName);
        }

        assertThat(JansiLoader.getNativeLibrarySourceUrl()).contains(nativeResourcePath.substring(1));
        Path extractedLibrary = Path.of(JansiLoader.getNativeLibraryPath());
        assertThat(extractedLibrary).exists().isRegularFile();
        assertThat(extractedLibrary.getParent()).isEqualTo(extractionDirectory);
        assertThat(extractedLibrary.getFileName().toString()).startsWith("jansi-").endsWith(mappedLibraryName);
        assertThat(Files.readAllBytes(extractedLibrary)).isEqualTo(resourceBytes(nativeResource));
        if (!OSInfo.getOSName().equals("Windows")) {
            assertThat(Files.isExecutable(extractedLibrary)).isTrue();
        }
    }

    private static byte[] resourceBytes(final URL resource) throws IOException {
        try (InputStream inputStream = resource.openStream()) {
            return inputStream.readAllBytes();
        }
    }

    private static String mapLibraryName(final String libraryName) {
        String mappedName = System.mapLibraryName(libraryName);
        if (mappedName.endsWith(".dylib")) {
            return mappedName.substring(0, mappedName.length() - ".dylib".length()) + ".jnilib";
        }
        return mappedName;
    }

    private static String setSystemProperty(final String key, final String value) {
        String previousValue = System.getProperty(key);
        System.setProperty(key, value);
        return previousValue;
    }

    private static String clearSystemProperty(final String key) {
        String previousValue = System.getProperty(key);
        System.clearProperty(key);
        return previousValue;
    }

    private static void restoreSystemProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
