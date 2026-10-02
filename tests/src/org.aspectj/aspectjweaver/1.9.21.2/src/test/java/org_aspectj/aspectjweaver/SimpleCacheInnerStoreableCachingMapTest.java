/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import org.aspectj.weaver.tools.cache.SimpleCache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleCacheInnerStoreableCachingMapTest {
    private static final String GENERATED_CACHE_SUBFOLDER = "panenka.cache";
    private static final String GENERATED_CACHE_INDEX = "cache.idx";
    private static final String GENERATED_CACHE_SEPARATOR = ";";
    private static final String PARENT_CLASS_NAME = "org_aspectj.aspectjweaver.RestoredCacheParent";
    private static final String FIRST_GENERATED_CLASS_NAME = "org_aspectj.aspectjweaver.FirstRestoredGeneratedType";
    private static final String SECOND_GENERATED_CLASS_NAME = "org_aspectj.aspectjweaver.SecondRestoredGeneratedType";
    private static final byte[] PARENT_BYTES = {11, 13, 17, 19};

    @TempDir
    Path cacheDirectory;

    @Test
    void deserializesStoredGeneratedClassIndexWhenCacheIsReopened() throws IOException {
        Path folder = cacheDirectory.resolve("restored-generated-index");
        Files.createDirectories(folder);
        PublicSimpleCache initialCache = new PublicSimpleCache(folder.toString(), true);
        Path generatedClassNamesFile = generatedClassNamesFile(folder);
        Path generatedCacheIndex = folder.resolve(GENERATED_CACHE_SUBFOLDER).resolve(GENERATED_CACHE_INDEX);

        initialCache.addGeneratedClassesNames(PARENT_CLASS_NAME, PARENT_BYTES, FIRST_GENERATED_CLASS_NAME);

        assertThat(generatedCacheIndex).exists();
        assertThat(readGeneratedClassNames(generatedClassNamesFile)).isEqualTo(FIRST_GENERATED_CLASS_NAME);

        PublicSimpleCache reopenedCache = new PublicSimpleCache(folder.toString(), true);
        reopenedCache.addGeneratedClassesNames(PARENT_CLASS_NAME, PARENT_BYTES, SECOND_GENERATED_CLASS_NAME);

        assertThat(readGeneratedClassNames(generatedClassNamesFile))
                .isEqualTo(FIRST_GENERATED_CLASS_NAME + GENERATED_CACHE_SEPARATOR + SECOND_GENERATED_CLASS_NAME);
    }

    private static String readGeneratedClassNames(Path generatedClassNamesFile) throws IOException {
        return Files.readString(generatedClassNamesFile, StandardCharsets.UTF_8);
    }

    private static Path generatedClassNamesFile(Path cacheFolder) {
        return cacheFolder.resolve(GENERATED_CACHE_SUBFOLDER).resolve(generatedClassNamesCacheKey());
    }

    private static String generatedClassNamesCacheKey() {
        CRC32 checksum = new CRC32();
        checksum.update(PARENT_BYTES);
        long value = checksum.getValue();
        return PARENT_CLASS_NAME.replace("/", ".") + "-" + value;
    }

    private static final class PublicSimpleCache extends SimpleCache {
        private PublicSimpleCache(String folder, boolean enabled) {
            super(folder, enabled);
        }
    }
}
