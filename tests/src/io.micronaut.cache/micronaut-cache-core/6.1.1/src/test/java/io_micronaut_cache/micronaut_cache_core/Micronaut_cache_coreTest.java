/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_cache.micronaut_cache_core;

import io.micronaut.cache.AsyncCache;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.CachePut;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(55)
public class Micronaut_cache_coreTest {
    private static final long WAIT_SECONDS = 10;

    @Test
    void cacheManagerProvidesSynchronousAndAsynchronousCacheOperations() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            CacheManager<?> manager = context.getBean(CacheManager.class);
            SyncCache<?> cache = manager.getCache("core-direct");

            assertThat(manager.getCacheNames()).contains("core-direct");
            assertThat(cache.getName()).isEqualTo("core-direct");
            assertThat(cache.get("missing", String.class)).isEmpty();

            cache.put("answer", "forty-two");
            assertThat(cache.get("answer", String.class)).contains("forty-two");
            assertThat(cache.putIfAbsent("answer", "another")).contains("forty-two");

            AsyncCache<?> asyncCache = cache.async();
            assertThat(asyncCache.getName()).isEqualTo("core-direct");
            assertThat(asyncCache.put("async-answer", "cached").get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isTrue();
            assertThat(asyncCache.get("async-answer", String.class)
                    .get(WAIT_SECONDS, TimeUnit.SECONDS)).contains("cached");
            assertThat(asyncCache.invalidate("async-answer")
                    .get(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(asyncCache.get("async-answer", String.class)
                    .get(WAIT_SECONDS, TimeUnit.SECONDS)).isEmpty();

            cache.invalidate("answer");
            assertThat(cache.get("answer", String.class)).isEmpty();
            cache.put("all", "removed");
            cache.invalidateAll();
            assertThat(cache.get("all", String.class)).isEmpty();
        }
    }

    @Test
    void cacheAnnotationsReadWriteAndInvalidateThroughPublicBeanMethods() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            CachedCatalog catalog = context.getBean(CachedCatalog.class);

            assertThat(catalog.lookup("alpha")).isEqualTo("alpha-1");
            assertThat(catalog.lookup("alpha")).isEqualTo("alpha-1");
            assertThat(catalog.syncLookupCount()).isEqualTo(1);

            assertThat(catalog.refresh("alpha", "published")).isEqualTo("published");
            assertThat(catalog.lookup("alpha")).isEqualTo("published");
            assertThat(catalog.syncLookupCount()).isEqualTo(1);

            catalog.invalidate("alpha");
            assertThat(catalog.lookup("alpha")).isEqualTo("alpha-2");
            assertThat(catalog.syncLookupCount()).isEqualTo(2);

            assertThat(catalog.refresh("beta", "second")).isEqualTo("second");
            assertThat(catalog.lookup("beta")).isEqualTo("second");
            catalog.invalidateAll();
            assertThat(catalog.lookup("beta")).isEqualTo("beta-3");
            assertThat(catalog.syncLookupCount()).isEqualTo(3);
        }
    }

    @Test
    void cacheableCompletionStageMethodCachesCompletedResults() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            CachedCatalog catalog = context.getBean(CachedCatalog.class);

            assertThat(catalog.asyncLookup("gamma").get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("gamma-1");
            assertThat(catalog.asyncLookup("gamma").get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("gamma-1");
            assertThat(catalog.asyncLookupCount()).isEqualTo(1);
        }
    }

    @Test
    void cacheableReactiveMethodCachesSingleResult() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            ReactiveCatalog catalog = context.getBean(ReactiveCatalog.class);
            Duration wait = Duration.ofSeconds(WAIT_SECONDS);

            assertThat(catalog.single("delta").block(wait)).isEqualTo("delta-1");
            assertThat(catalog.single("delta").block(wait)).isEqualTo("delta-1");
            assertThat(catalog.singleLookupCount()).isEqualTo(1);
        }
    }

    @Test
    void cacheableMethodUsesAllSelectedParametersForItsKey() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            MultiParameterCatalog catalog = context.getBean(MultiParameterCatalog.class);

            assertThat(catalog.lookup("books", 1)).isEqualTo("books-1-1");
            assertThat(catalog.lookup("books", 1)).isEqualTo("books-1-1");
            assertThat(catalog.lookup("books", 2)).isEqualTo("books-2-2");
            assertThat(catalog.lookup("music", 1)).isEqualTo("music-1-3");
            assertThat(catalog.lookupCount()).isEqualTo(3);
        }
    }

    @Test
    void cacheableConditionCanDisableCachingForSelectedOperation() {
        try (ApplicationContext context = ApplicationContext.run(Map.of())) {
            ConditionalCatalog catalog = context.getBean(ConditionalCatalog.class);

            assertThat(catalog.lookup("delta")).isEqualTo("delta-1");
            assertThat(catalog.lookup("delta")).isEqualTo("delta-2");
            assertThat(catalog.lookupCount()).isEqualTo(2);
        }
    }

    @Singleton
    public static class CachedCatalog {
        private int syncLookupCount;
        private int asyncLookupCount;

        @Cacheable(cacheNames = "core-annotated", parameters = "key")
        public String lookup(String key) {
            syncLookupCount++;
            return key + "-" + syncLookupCount;
        }

        @CachePut(cacheNames = "core-annotated", parameters = "key")
        public String refresh(String key, String value) {
            return value;
        }

        @CacheInvalidate(cacheNames = "core-annotated", parameters = "key")
        public void invalidate(String key) {
        }

        @CacheInvalidate(cacheNames = "core-annotated", all = true)
        public void invalidateAll() {
        }

        @Cacheable(cacheNames = "core-async", parameters = "key")
        public CompletableFuture<String> asyncLookup(String key) {
            asyncLookupCount++;
            return CompletableFuture.completedFuture(key + "-" + asyncLookupCount);
        }

        public int syncLookupCount() {
            return syncLookupCount;
        }

        public int asyncLookupCount() {
            return asyncLookupCount;
        }
    }

    @Singleton
    public static class ReactiveCatalog {
        private int singleLookupCount;

        @Cacheable(cacheNames = "core-reactive-single", parameters = "key")
        public Mono<String> single(String key) {
            singleLookupCount++;
            return Mono.just(key + "-" + singleLookupCount);
        }

        public int singleLookupCount() {
            return singleLookupCount;
        }
    }

    @Singleton
    public static class MultiParameterCatalog {
        private int lookupCount;

        @Cacheable(
                cacheNames = "core-multi-parameter",
                parameters = {"category", "number"})
        public String lookup(String category, int number) {
            lookupCount++;
            return category + "-" + number + "-" + lookupCount;
        }

        public int lookupCount() {
            return lookupCount;
        }
    }

    @Singleton
    public static class ConditionalCatalog {
        private int lookupCount;

        @Cacheable(
                cacheNames = "core-conditional",
                parameters = "key",
                condition = "false")
        public String lookup(String key) {
            lookupCount++;
            return key + "-" + lookupCount;
        }

        public int lookupCount() {
            return lookupCount;
        }
    }
}
