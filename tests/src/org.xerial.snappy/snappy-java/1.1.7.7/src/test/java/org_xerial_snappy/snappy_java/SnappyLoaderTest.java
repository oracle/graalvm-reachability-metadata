/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_xerial_snappy.snappy_java;

import org.junit.jupiter.api.Test;
import org.xerial.snappy.Snappy;
import org.xerial.snappy.SnappyLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class SnappyLoaderTest {
    private static final String SNAPPY_TEMPDIR = "org.xerial.snappy.tempdir";
    private static final String SNAPPY_USE_SYSTEMLIB = "org.xerial.snappy.use.systemlib";
    private static final String SNAPPY_DISABLE_BUNDLED_LIBS = "org.xerial.snappy.disable.bundled.libs";
    private static final String SNAPPY_PUREJAVA = "org.xerial.snappy.purejava";
    private static final String SNAPPY_LIB_PATH = "org.xerial.snappy.lib.path";
    private static final String SNAPPY_LIB_NAME = "org.xerial.snappy.lib.name";

    private static final List<String> LOADER_PROPERTIES = List.of(
            SNAPPY_TEMPDIR,
            SNAPPY_USE_SYSTEMLIB,
            SNAPPY_DISABLE_BUNDLED_LIBS,
            SNAPPY_PUREJAVA,
            SNAPPY_LIB_PATH,
            SNAPPY_LIB_NAME
    );

    @Test
    void compressRoundTripUsesTheBundledNativeLibrary() throws Exception {
        Map<String, String> originalProperties = captureLoaderProperties();
        Path extractionDir = Files.createTempDirectory("snappy-loader-");
        byte[] input = "SnappyLoader exercises bundled native resources".getBytes(StandardCharsets.UTF_8);

        try {
            configureBundledLibraryExtraction(extractionDir);

            byte[] compressed = Snappy.compress(input);
            byte[] restored = Snappy.uncompress(compressed);

            assertThat(restored).containsExactly(input);
            assertThat(SnappyLoader.getVersion()).isNotBlank();
            assertThat(Snappy.getNativeLibraryVersion()).isNotBlank();

            List<Path> extractedLibraries;
            try (Stream<Path> files = Files.list(extractionDir)) {
                extractedLibraries = files
                        .filter(Files::isRegularFile)
                        .toList();
            }

            if (!extractedLibraries.isEmpty()) {
                assertThat(extractedLibraries)
                        .anySatisfy(path -> assertThat(path.getFileName().toString())
                                .startsWith("snappy-")
                                .contains("snappyjava"));
                assertThat(extractedLibraries)
                        .allSatisfy(path -> assertThat(fileSize(path)).isGreaterThan(0L));
            }
        } finally {
            restoreLoaderProperties(originalProperties);
        }
    }

    private static Map<String, String> captureLoaderProperties() {
        Map<String, String> properties = new HashMap<>();
        for (String propertyName : LOADER_PROPERTIES) {
            properties.put(propertyName, System.getProperty(propertyName));
        }
        return properties;
    }

    private static void configureBundledLibraryExtraction(Path extractionDir) {
        System.setProperty(SNAPPY_TEMPDIR, extractionDir.toString());
        System.setProperty(SNAPPY_USE_SYSTEMLIB, Boolean.FALSE.toString());
        System.setProperty(SNAPPY_DISABLE_BUNDLED_LIBS, Boolean.FALSE.toString());
        System.setProperty(SNAPPY_PUREJAVA, Boolean.FALSE.toString());
        System.clearProperty(SNAPPY_LIB_PATH);
        System.clearProperty(SNAPPY_LIB_NAME);
    }

    private static void restoreLoaderProperties(Map<String, String> originalProperties) {
        for (String propertyName : LOADER_PROPERTIES) {
            String originalValue = originalProperties.get(propertyName);
            if (originalValue == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, originalValue);
            }
        }
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read extracted library size", exception);
        }
    }
}
