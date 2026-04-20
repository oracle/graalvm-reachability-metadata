/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api;

import java.net.URI;
import java.util.Properties;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Caching_CachingProviderRegistryTest {

    private static final String PROVIDER_CLASS_NAME =
        "javax_cache.cache_api.Caching_CachingProviderRegistryTest$TestCachingProvider";

    @Test
    void loadsAndCachesExplicitCachingProvider() {
        ClassLoader classLoader = getClass().getClassLoader();

        CachingProvider provider = Caching.getCachingProvider(PROVIDER_CLASS_NAME, classLoader);
        CachingProvider cachedProvider = Caching.getCachingProvider(PROVIDER_CLASS_NAME, classLoader);

        assertThat(provider.getClass().getName()).isEqualTo(PROVIDER_CLASS_NAME);
        assertThat(provider).isSameAs(cachedProvider);
    }

    public static final class TestCachingProvider implements CachingProvider {

        public TestCachingProvider() {
        }

        @Override
        public CacheManager getCacheManager(URI uri, ClassLoader classLoader, Properties properties) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public ClassLoader getDefaultClassLoader() {
            return getClass().getClassLoader();
        }

        @Override
        public URI getDefaultURI() {
            return URI.create("test://caching-provider");
        }

        @Override
        public Properties getDefaultProperties() {
            return new Properties();
        }

        @Override
        public CacheManager getCacheManager(URI uri, ClassLoader classLoader) {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public CacheManager getCacheManager() {
            throw new UnsupportedOperationException("Not needed for this test");
        }

        @Override
        public void close() {
        }

        @Override
        public void close(ClassLoader classLoader) {
        }

        @Override
        public void close(URI uri, ClassLoader classLoader) {
        }

        @Override
        public boolean isSupported(OptionalFeature optionalFeature) {
            return false;
        }
    }
}
