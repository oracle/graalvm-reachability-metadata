/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.aspectj.weaver.tools.cache.SimpleCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SimpleCacheInnerStoreableCachingMapTest {
    private static final String GENERATED_CACHE_DIRECTORY = "panenka.cache";
    private static final String CACHE_INDEX_FILE = "cache.idx";

    @TempDir
    Path temporaryDirectory;

    @Test
    void reopensStoredGeneratedClassIndex() throws IOException {
        String parentClassName = "org_aspectj.aspectjweaver.StoreableCachingMapParent";
        byte[] wovenParentBytes = bytes("storeable-caching-map-parent");
        String firstGeneratedClassName = "org_aspectj.aspectjweaver.generated.FirstStoreableCachingMapGenerated";
        String secondGeneratedClassName = "org_aspectj.aspectjweaver.generated.SecondStoreableCachingMapGenerated";

        TestableSimpleCache firstCache = new TestableSimpleCache(temporaryDirectory);
        firstCache.addGeneratedClassesNames(parentClassName, wovenParentBytes, firstGeneratedClassName);

        Path generatedCacheDirectory = temporaryDirectory.resolve(GENERATED_CACHE_DIRECTORY);
        assertThat(generatedCacheDirectory.resolve(CACHE_INDEX_FILE)).isRegularFile();
        Path storedGeneratedClassNames = singleStoredValueFile(generatedCacheDirectory);
        assertThat(Files.readString(storedGeneratedClassNames, StandardCharsets.UTF_8))
                .isEqualTo(firstGeneratedClassName);

        TestableSimpleCache reopenedCache = new TestableSimpleCache(temporaryDirectory);
        reopenedCache.addGeneratedClassesNames(parentClassName, wovenParentBytes, secondGeneratedClassName);

        assertThat(Files.readString(storedGeneratedClassNames, StandardCharsets.UTF_8))
                .isEqualTo(firstGeneratedClassName + ";" + secondGeneratedClassName);
    }

    private static Path singleStoredValueFile(Path generatedCacheDirectory) throws IOException {
        try (Stream<Path> paths = Files.list(generatedCacheDirectory)) {
            List<Path> storedValueFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> !CACHE_INDEX_FILE.equals(path.getFileName().toString()))
                    .collect(Collectors.toList());
            assertThat(storedValueFiles).hasSize(1);
            return storedValueFiles.get(0);
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static final class TestableSimpleCache extends SimpleCache {
        private TestableSimpleCache(Path folder) {
            super(folder.toString(), true);
        }
    }
}
