/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_geronimo_specs.geronimo_jcache_1_0_spec;

import org.junit.jupiter.api.Test;

import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.OptionalFeature;
import javax.cache.spi.CachingProvider;
import java.net.URI;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingInnerCachingProviderRegistryTest {
    @Test
    void loadsProviderByClassNameWithRequestedClassLoader() {
        ClassLoader providerClassLoader = new DelegatingClassLoader(TestCachingProvider.class.getClassLoader());

        CachingProvider provider = Caching.getCachingProvider(TestCachingProvider.class.getName(), providerClassLoader);

        assertThat(provider).isInstanceOf(TestCachingProvider.class);
    }

    private static final class DelegatingClassLoader extends ClassLoader {
        private DelegatingClassLoader(ClassLoader parent) {
            super(parent);
        }
    }

    public static class TestCachingProvider implements CachingProvider {
        public TestCachingProvider() {
        }

        @Override
        public CacheManager getCacheManager(URI uri, ClassLoader classLoader, Properties properties) {
            return null;
        }

        @Override
        public ClassLoader getDefaultClassLoader() {
            return TestCachingProvider.class.getClassLoader();
        }

        @Override
        public URI getDefaultURI() {
            return URI.create("urn:test-cache-provider");
        }

        @Override
        public Properties getDefaultProperties() {
            return new Properties();
        }

        @Override
        public CacheManager getCacheManager(URI uri, ClassLoader classLoader) {
            return null;
        }

        @Override
        public CacheManager getCacheManager() {
            return null;
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
