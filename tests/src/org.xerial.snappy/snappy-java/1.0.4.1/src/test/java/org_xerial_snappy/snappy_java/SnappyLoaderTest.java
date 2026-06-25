/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyInputStream;
import org.xerial.snappy.SnappyLoader;
import org.xerial.snappy.SnappyOutputStream;

public class SnappyLoaderTest {
    @Test
    void loadsBundledNativeLibraryAndRoundTripsData(@TempDir Path tempDir) throws Exception {
        System.setProperty(SnappyLoader.KEY_SNAPPY_TEMPDIR, tempDir.toString());
        String extractedLibraryName = "snappy-" + SnappyLoader.getVersion() + "-"
                + System.mapLibraryName("snappyjava");
        Path extractedLibrary = tempDir.resolve(extractedLibraryName);
        Files.writeString(extractedLibrary, "stale native library placeholder", UTF_8);

        try {
            String message = "snappy-java compresses repeatable byte content";
            byte[] input = message.repeat(8).getBytes(UTF_8);

            byte[] compressed = Snappy.compress(input);
            assertThat(Snappy.isValidCompressedBuffer(compressed)).isTrue();
            assertThat(Snappy.uncompress(compressed)).isEqualTo(input);

            ByteArrayOutputStream streamBytes = new ByteArrayOutputStream();
            try (SnappyOutputStream snappyOutputStream = new SnappyOutputStream(streamBytes)) {
                snappyOutputStream.write(input);
            }
            try (SnappyInputStream snappyInputStream = new SnappyInputStream(
                    new ByteArrayInputStream(streamBytes.toByteArray()))) {
                assertThat(snappyInputStream.readAllBytes()).isEqualTo(input);
            }

            assertThat(SnappyLoader.isNativeLibraryLoaded()).isTrue();
            assertThat(Files.size(extractedLibrary)).isGreaterThan(0L);
        } catch (Error error) {
            rethrowUnlessUnsupportedDynamicClassDefinition(error);
        }
    }

    private static void rethrowUnlessUnsupportedDynamicClassDefinition(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
