/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_cache.cache_api.optional;

import org.junit.jupiter.api.Test;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;

import static javax.cache.expiry.Duration.ONE_HOUR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CacheTest {
    @Test
    public void simpleAPITypeEnforcement() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> config = new MutableConfiguration<>();
        config.setStoreByValue(false).setTypes(String.class, Integer.class).setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR))
                .setStatisticsEnabled(true);
        cacheManager.createCache("simpleOptionalCache", config);
        Cache<String, Integer> cache = Caching.getCache("simpleOptionalCache", String.class, Integer.class);
        cache.put("key", 1);
        assertEquals(1, cache.get("key"));
        cache.remove("key");
        assertNull(cache.get("key"));
    }
}
