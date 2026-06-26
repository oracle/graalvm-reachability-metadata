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

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

public class SnappyLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsBundledNativeLibraryAfterReplacingStaleExtractedCopy() throws Exception {
        System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, tempDir.toString());
        System.clearProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_DISABLE_BUNDLED_LIBS);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_PATH);
        System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_NAME);

        String libraryFileName = System.mapLibraryName("snappyjava");
        Path staleExtractedLibrary = tempDir.resolve("snappy-" + SnappyLoader.getVersion() + "-" + libraryFileName);
        Files.writeString(staleExtractedLibrary, "stale native library", StandardCharsets.UTF_8);

        byte[] input = "SnappyLoader extracts and loads the bundled JNI library".getBytes(StandardCharsets.UTF_8);
        try {
            byte[] compressed = Snappy.compress(input);
            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);
            assertThat(SnappyLoader.isNativeLibraryLoaded()).isTrue();
            assertThat(Files.size(staleExtractedLibrary)).isGreaterThan((long) "stale native library".length());
        } catch (Error error) {
            rethrowUnlessUnsupportedFeatureError(error);
        } finally {
            System.clearProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR);
            System.clearProperty(SnappyLoader.KEY_SNAPPY_USE_SYSTEMLIB);
            System.clearProperty(SnappyLoader.KEY_SNAPPY_DISABLE_BUNDLED_LIBS);
            System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_PATH);
            System.clearProperty(SnappyLoader.KEY_SNAPPY_LIB_NAME);
        }
    }

    private static void rethrowUnlessUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
