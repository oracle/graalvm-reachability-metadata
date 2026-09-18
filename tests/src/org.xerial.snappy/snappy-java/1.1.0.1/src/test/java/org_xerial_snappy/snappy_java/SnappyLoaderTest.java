/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

public class SnappyLoaderTest {
    private static final String STALE_LIBRARY_CONTENT = "stale native library";

    @TempDir
    Path tempDir;

    @Test
    void loadsBundledNativeLibrary() throws Exception {
        String previousTempDirectory = System.getProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR);
        Path nativeLibraryDirectory = tempDir.resolve("native-library");
        Files.createDirectories(nativeLibraryDirectory);

        String libraryFileName = System.mapLibraryName("snappyjava");
        String extractedLibraryPrefix = "snappy-" + SnappyLoader.getVersion() + "-";
        Path staleLibrary = nativeLibraryDirectory.resolve(extractedLibraryPrefix + libraryFileName);
        Files.writeString(staleLibrary, STALE_LIBRARY_CONTENT, StandardCharsets.UTF_8);

        try {
            System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, nativeLibraryDirectory.toString());
            byte[] input = "SnappyLoader extracts the bundled JNI library".getBytes(StandardCharsets.UTF_8);
            byte[] compressed = Snappy.compress(input);

            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
            assertThat(Files.readString(staleLibrary)).isEqualTo(STALE_LIBRARY_CONTENT);
            Path extractedLibrary = findExtractedLibrary(
                    nativeLibraryDirectory, extractedLibraryPrefix, staleLibrary);
            assertThat(Files.size(extractedLibrary)).isGreaterThan(Files.size(staleLibrary));
        } finally {
            restoreProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, previousTempDirectory);
        }
    }

    private static Path findExtractedLibrary(Path directory, String prefix, Path staleLibrary) throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !path.equals(staleLibrary))
                    .filter(path -> path.getFileName().toString().startsWith(prefix))
                    .findFirst()
                    .orElseThrow();
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
