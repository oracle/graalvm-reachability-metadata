/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_azure.msal4j_persistence_extension;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.aad.msal4jextensions.persistence.CacheFileAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class CacheFileAccessorTest {
    @TempDir
    private Path cacheDirectory;

    @Test
    void writesReadsUpdatesAndDeletesCacheFile() throws IOException {
        Path cacheFile = cacheDirectory.resolve("token-cache.json");
        CacheFileAccessor cacheAccessor = new CacheFileAccessor(cacheFile.toString());
        byte[] cachedTokenData = {1, 2, 3, 4};

        cacheAccessor.write(cachedTokenData);

        assertThat(cacheAccessor.read()).containsExactly(cachedTokenData);

        Files.setLastModifiedTime(cacheFile, FileTime.fromMillis(1));
        cacheAccessor.updateCacheFileLastModifiedTime();
        assertThat(Files.getLastModifiedTime(cacheFile).toMillis()).isGreaterThan(1);

        cacheAccessor.delete();
        assertThat(cacheFile).doesNotExist();

        cacheAccessor.delete();
        assertThat(cacheFile).doesNotExist();
    }
}
