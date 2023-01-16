/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api.core;

import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.Configuration;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;

import static javax.cache.expiry.Duration.ONE_HOUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CacheTest {
    @Test
    public void simpleCache() {
        CacheManager manager = Caching.getCachingProvider().getCacheManager();
        Configuration<Integer, String> configuration = new MutableConfiguration<Integer, String>().setTypes(Integer.class, String.class);
        assertThat(manager.getCache("simpleCache22")).isNull();
        Cache<Integer, String> simpleCache = manager.createCache("simpleCache22", configuration);
        simpleCache.put(2, "value");
        assertThat(simpleCache.get(2)).isEqualTo("value");
    }

    @Test
    public void simpleAPITypeEnforcement() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> config = new MutableConfiguration<String, Integer>()
                .setStoreByValue(true).setTypes(String.class, Integer.class)
                .setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        cacheManager.createCache("simpleCache", config);
        Cache<String, Integer> cache = Caching.getCache("simpleCache", String.class, Integer.class);
        cache.put("key", 1);
        assertEquals(1, cache.get("key"));
        cache.remove("key");
        assertNull(cache.get("key"));
    }

    @Test
    public void simpleAPITypeEnforcementUsingCaching() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> config = new MutableConfiguration<>();
        config.setTypes(String.class, Integer.class).setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        cacheManager.createCache("simpleCache2", config);
        Cache<String, Integer> cache = Caching.getCache("simpleCache2", String.class, Integer.class);
        cache.put("key", 1);
        assertEquals(1, cache.get("key"));
        cache.remove("key");
        assertNull(cache.get("key"));
    }

    @Test
    public void simpleAPIWithGenericsAndNoTypeEnforcement() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> config = new MutableConfiguration<>();
        config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        cacheManager.createCache("sampleCache3", config);
        Cache<String, Integer> cache = cacheManager.getCache("sampleCache3");
        cache.put("key", 1);
        assertThat(cache.get("key")).isEqualTo(1);
        cache.remove("key");
        assertNull(cache.get("key"));
    }


    @Test
    public void simpleAPINoGenericsAndNoTypeEnforcement() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        String cacheName = "sampleCache";
        MutableConfiguration<Object, Object> config = new MutableConfiguration<>()
                .setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        cacheManager.createCache(cacheName, config);
        Cache<Object, Object> cache = cacheManager.getCache(cacheName);
        cache.put("key", 1);
        cache.put(1, "key");
        assertEquals(1, (Integer) cache.get("key"));
        cache.remove("key");
        assertNull(cache.get("key"));
    }

    @Test
    public void simpleAPITypeEnforcementObject() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<Object, Object> config = new MutableConfiguration<>()
                .setTypes(Object.class, Object.class).setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        cacheManager.createCache("simpleCache4", config);
        Cache<Object, Object> cache = Caching.getCache("simpleCache4", Object.class, Object.class);
        cache.put("key", 1);
        assertEquals(1, cache.get("key"));
        cache.remove("key");
        assertNull(cache.get("key"));
    }
}
