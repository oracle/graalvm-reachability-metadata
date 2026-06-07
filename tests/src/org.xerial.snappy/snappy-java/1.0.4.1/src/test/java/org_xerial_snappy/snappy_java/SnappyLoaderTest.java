/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class SnappyLoaderTest {
    @Test
    void loadsBundledNativeLibraryAndRoundTripsPayload(@TempDir final Path tempDirectory) throws Exception {
        final String previousTempDirectory = System.getProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR);
        System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, tempDirectory.toString());

        try {
            createStaleExtractedLibrary(tempDirectory);

            final byte[] input = "Snappy loader integration payload".getBytes(StandardCharsets.UTF_8);
            final byte[] compressed = Snappy.compress(input);

            assertThat(Snappy.isValidCompressedBuffer(compressed)).isTrue();
            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
            assertThat(SnappyLoader.isNativeLibraryLoaded()).isTrue();
        } catch (final Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            restoreSystemProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, previousTempDirectory);
        }
    }

    private static void createStaleExtractedLibrary(final Path tempDirectory) throws IOException {
        final String version = SnappyLoader.getVersion();
        final String libraryFileName = System.mapLibraryName("snappyjava");
        final Path staleLibrary = tempDirectory.resolve("snappy-" + version + "-" + libraryFileName);

        Files.write(staleLibrary, "stale native library".getBytes(StandardCharsets.UTF_8));
    }

    private static void restoreSystemProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }
}
