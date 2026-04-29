/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.aspectj.bridge.MessageHandler;
import org.aspectj.weaver.tools.cache.CachedClassEntry;
import org.aspectj.weaver.tools.cache.CachedClassReference;
import org.aspectj.weaver.tools.cache.WeavedClassCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AbstractIndexedFileCacheBackingTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsCacheIndexAndReadsItWhenCacheIsReopened() {
        String originalCacheDirectory = System.getProperty("aj.weaving.cache.dir");
        try {
            System.setProperty("aj.weaving.cache.dir", temporaryDirectory.toString());
            ClassLoader classLoader = AbstractIndexedFileCacheBackingTest.class.getClassLoader();
            List<String> cacheScope = Collections.singletonList("abstract-indexed-file-cache-backing-scope");
            byte[] originalBytes = "class-bytes-before-weaving".getBytes(StandardCharsets.UTF_8);
            byte[] weavedBytes = "class-bytes-after-weaving".getBytes(StandardCharsets.UTF_8);

            WeavedClassCache cache = createCache(classLoader, cacheScope);
            CachedClassReference cacheKey = cache.createCacheKey("com.example.CachedType", originalBytes);

            cache.put(cacheKey, originalBytes, weavedBytes);

            assertThat(temporaryDirectory.resolve(cache.getName()).resolve("cache.idx")).isRegularFile();
            assertThat(cache.get(cacheKey, originalBytes).getBytes()).containsExactly(weavedBytes);

            WeavedClassCache reopenedCache = createCache(classLoader, cacheScope);
            CachedClassEntry restoredEntry = reopenedCache.get(cacheKey, originalBytes);

            assertThat(restoredEntry).isNotNull();
            assertThat(restoredEntry.isWeaved()).isTrue();
            assertThat(restoredEntry.getClassName()).isEqualTo("com.example.CachedType");
            assertThat(restoredEntry.getBytes()).containsExactly(weavedBytes);
        } finally {
            restoreSystemProperty("aj.weaving.cache.dir", originalCacheDirectory);
        }
    }

    private static WeavedClassCache createCache(ClassLoader classLoader, List<String> cacheScope) {
        WeavedClassCache cache = WeavedClassCache.createCache(classLoader, cacheScope, null, new MessageHandler());
        assertThat(cache).isNotNull();
        return cache;
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
