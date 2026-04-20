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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.Configuration;
import javax.cache.spi.CachingProvider;

import org.hibernate.cache.jcache.ConfigSettings;
import org.hibernate.cache.jcache.internal.JCacheRegionFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JCacheRegionFactoryTest {

    @Test
    void startsWithAnExplicitCacheManagerClass() {
        InstantiableCacheManager.reset();

        JCacheRegionFactory regionFactory = new JCacheRegionFactory();
        regionFactory.start(null, Map.<String, Object>of(ConfigSettings.CACHE_MANAGER, InstantiableCacheManager.class));

        try {
            assertThat(regionFactory.getCacheManager()).isInstanceOf(InstantiableCacheManager.class);
            assertThat(InstantiableCacheManager.INSTANTIATION_COUNT.get()).isEqualTo(1);
            assertThat(InstantiableCacheManager.CLOSED.get()).isFalse();
        } finally {
            regionFactory.stop();
        }

        assertThat(InstantiableCacheManager.CLOSED.get()).isTrue();
    }

    public static final class InstantiableCacheManager implements CacheManager {
        static final AtomicInteger INSTANTIATION_COUNT = new AtomicInteger();
        static final AtomicBoolean CLOSED = new AtomicBoolean();

        public InstantiableCacheManager() {
            INSTANTIATION_COUNT.incrementAndGet();
        }

        static void reset() {
            INSTANTIATION_COUNT.set(0);
            CLOSED.set(false);
        }

        @Override
        public CachingProvider getCachingProvider() {
            return null;
        }

        @Override
        public URI getURI() {
            return URI.create("test://jcache-region-factory");
        }

        @Override
        public ClassLoader getClassLoader() {
            return JCacheRegionFactoryTest.class.getClassLoader();
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
            CLOSED.set(true);
        }

        @Override
        public boolean isClosed() {
            return CLOSED.get();
        }

        @Override
        public <T> T unwrap(Class<T> clazz) {
            if (clazz.isInstance(this)) {
                return clazz.cast(this);
            }
            return null;
        }
    }
}
