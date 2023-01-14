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
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.integration.CompletionListenerFuture;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import static javax.cache.expiry.Duration.ONE_HOUR;
import static org.assertj.core.api.Assertions.assertThat;

public class CompletionListenerTest {
    @Test
    public void testCompletionListener() {
        CacheManager cacheManager = Caching.getCachingProvider().getCacheManager();
        MutableConfiguration<String, Integer> config = new MutableConfiguration<String, Integer>()
                .setTypes(String.class, Integer.class).setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(ONE_HOUR)).setStatisticsEnabled(true);
        Cache<String, Integer> cache = cacheManager.createCache("simpleCache3", config);
        HashSet<String> keys = new HashSet<>();
        keys.add("23432lkj");
        keys.add("4fsdldkj");
        CompletionListenerFuture future = new CompletionListenerFuture();
        cache.loadAll(keys, true, future);
        try {
            future.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.getCause();
        }
        assertThat(future.isDone()).isEqualTo(true);
    }
}
