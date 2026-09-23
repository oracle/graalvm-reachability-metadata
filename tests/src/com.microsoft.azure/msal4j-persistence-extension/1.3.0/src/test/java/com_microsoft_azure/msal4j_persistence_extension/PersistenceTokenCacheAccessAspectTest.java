/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_azure.msal4j_persistence_extension;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.aad.msal4j.IAccount;
import com.microsoft.aad.msal4j.ITokenCache;
import com.microsoft.aad.msal4j.ITokenCacheAccessContext;
import com.microsoft.aad.msal4jextensions.PersistenceSettings;
import com.microsoft.aad.msal4jextensions.PersistenceTokenCacheAccessAspect;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class PersistenceTokenCacheAccessAspectTest {
    private static final String SERIALIZED_CACHE = "{\"AccessToken\":{\"entry\":\"persisted\"}}";

    @TempDir
    private Path cacheDirectory;

    @Test
    void persistsAndReloadsTokenCacheUsingTheAccessAspect() throws IOException {
        PersistenceSettings settings = PersistenceSettings.builder("token-cache.json", cacheDirectory)
                .setLinuxUseUnprotectedFileAsCacheStorage(true)
                .build();
        PersistenceTokenCacheAccessAspect writer = new PersistenceTokenCacheAccessAspect(settings);
        RecordingTokenCache writeCache = new RecordingTokenCache(SERIALIZED_CACHE);
        TestTokenCacheAccessContext writeContext = new TestTokenCacheAccessContext(writeCache, true);

        writer.beforeCacheAccess(writeContext);
        writer.afterCacheAccess(writeContext);

        PersistenceTokenCacheAccessAspect reader = new PersistenceTokenCacheAccessAspect(settings);
        RecordingTokenCache readCache = new RecordingTokenCache("unused");
        TestTokenCacheAccessContext readContext = new TestTokenCacheAccessContext(readCache, false);
        reader.beforeCacheAccess(readContext);
        reader.afterCacheAccess(readContext);

        assertThat(readCache.deserializedValue).isEqualTo(SERIALIZED_CACHE);
        assertThat(readCache.deserializeCount).isEqualTo(1);

        reader.beforeCacheAccess(readContext);
        reader.afterCacheAccess(readContext);

        assertThat(readCache.deserializeCount).isEqualTo(1);
    }

    private static final class RecordingTokenCache implements ITokenCache {
        private final String serializedValue;
        private String deserializedValue;
        private int deserializeCount;

        private RecordingTokenCache(String serializedValue) {
            this.serializedValue = serializedValue;
        }

        @Override
        public void deserialize(String data) {
            deserializedValue = data;
            deserializeCount++;
        }

        @Override
        public String serialize() {
            return serializedValue;
        }
    }

    private static final class TestTokenCacheAccessContext implements ITokenCacheAccessContext {
        private final ITokenCache tokenCache;
        private final boolean cacheChanged;

        private TestTokenCacheAccessContext(ITokenCache tokenCache, boolean cacheChanged) {
            this.tokenCache = tokenCache;
            this.cacheChanged = cacheChanged;
        }

        @Override
        public ITokenCache tokenCache() {
            return tokenCache;
        }

        @Override
        public String clientId() {
            return "test-client";
        }

        @Override
        public IAccount account() {
            return null;
        }

        @Override
        public boolean hasCacheChanged() {
            return cacheChanged;
        }
    }
}
