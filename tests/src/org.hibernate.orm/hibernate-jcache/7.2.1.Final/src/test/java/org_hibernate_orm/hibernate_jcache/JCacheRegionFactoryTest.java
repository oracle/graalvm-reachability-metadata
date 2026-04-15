/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_jcache;

import java.net.URI;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;

import org.hibernate.boot.spi.SessionFactoryOptions;
import org.hibernate.cache.jcache.ConfigSettings;
import org.hibernate.cache.jcache.internal.JCacheRegionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCacheRegionFactoryTest {
    @Test
    void startInstantiatesExplicitCacheManagerClass() {
        JCacheRegionFactory factory = new JCacheRegionFactory();
        SessionFactoryOptions sessionFactoryOptions = null;

        factory.start(sessionFactoryOptions, Map.of(ConfigSettings.CACHE_MANAGER, ExplicitCacheManager.class));

        CacheManager cacheManager = factory.getCacheManager();
        assertThat(cacheManager).isInstanceOf(ExplicitCacheManager.class);
        assertThat(((ExplicitCacheManager) cacheManager).isClosed()).isFalse();

        factory.stop();

        assertThat(((ExplicitCacheManager) cacheManager).isClosed()).isTrue();
        assertThat(factory.getCacheManager()).isNull();
    }

    public static final class ExplicitCacheManager implements CacheManager {
        private boolean closed;

        public ExplicitCacheManager() {
        }

        @Override
        public CachingProvider getCachingProvider() {
            return null;
        }

        @Override
        public URI getURI() {
            return URI.create("urn:test-cache-manager");
        }

        @Override
        public ClassLoader getClassLoader() {
            return ExplicitCacheManager.class.getClassLoader();
        }

        @Override
        public Properties getProperties() {
            return new Properties();
        }

        @Override
        public <K, V, C extends Configuration<K, V>> Cache<K, V> createCache(String cacheName, C configuration) {
            return null;
        }

        @Override
        public <K, V> Cache<K, V> getCache(String cacheName, Class<K> keyType, Class<V> valueType) {
            return null;
        }

        @Override
        public <K, V> Cache<K, V> getCache(String cacheName) {
            return null;
        }

        @Override
        public Iterable<String> getCacheNames() {
            return Collections.emptyList();
        }

        @Override
        public void destroyCache(String cacheName) {
        }

        @Override
        public void enableManagement(String cacheName, boolean enabled) {
        }

        @Override
        public void enableStatistics(String cacheName, boolean enabled) {
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
            return null;
        }
    }
}
