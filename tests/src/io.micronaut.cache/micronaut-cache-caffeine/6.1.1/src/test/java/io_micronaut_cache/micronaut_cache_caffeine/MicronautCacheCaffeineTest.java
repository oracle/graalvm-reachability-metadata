/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_cache.micronaut_cache_caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import com.github.benmanes.caffeine.cache.Weigher;
import io.micronaut.cache.AsyncCache;
import io.micronaut.cache.CacheInfo;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.caffeine.DefaultSyncCache;
import io.micronaut.cache.caffeine.configuration.CaffeineCacheConfiguration;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.ApplicationConfiguration;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import static org.assertj.core.api.Assertions.assertThat;

public class MicronautCacheCaffeineTest {
    private static final long WAIT_SECONDS = 10;

    @Test
    void configuredCacheSupportsSynchronousOperationsAndConversion() {
        try (ApplicationContext context = configuredContext()) {
            CacheManager<?> manager = context.getBean(CacheManager.class);
            SyncCache<?> cache = manager.getCache("catalog");

            assertThat(manager.getCacheNames()).contains("catalog");
            assertThat(cache.getName()).isEqualTo("catalog");
            assertThat(cache.getNativeCache()).isInstanceOf(Cache.class);
            assertThat(cache.get("missing", String.class)).isEmpty();

            cache.put("quantity", "42");
            assertThat(cache.get("quantity", Integer.class)).contains(42);

            CountingSupplier supplier = new CountingSupplier("generated");
            assertThat(cache.get("computed", String.class, supplier)).isEqualTo("generated");
            assertThat(cache.get("computed", String.class, supplier)).isEqualTo("generated");
            assertThat(supplier.invocations()).isEqualTo(1);

            assertThat(cache.putIfAbsent("stable", "first")).isEmpty();
            assertThat(cache.putIfAbsent("stable", "second")).contains("first");
            assertThat(cache.get("stable", String.class)).contains("first");

            cache.invalidate("quantity");
            assertThat(cache.get("quantity", Integer.class)).isEmpty();
            cache.invalidateAll();
            assertThat(cache.get("stable", String.class)).isEmpty();
        }
    }

    @Test
    void asynchronousViewPerformsCacheOperations() throws Exception {
        try (ApplicationContext context = configuredContext()) {
            SyncCache<?> cache = context.getBean(CacheManager.class).getCache("catalog");
            AsyncCache<?> asyncCache = cache.async();

            assertThat(asyncCache.getName()).isEqualTo("catalog");
            assertThat(asyncCache.getNativeCache()).isSameAs(cache.getNativeCache());
            assertThat(asyncCache.put("alpha", "one").get(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(asyncCache.get("alpha", String.class).get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .contains("one");
            assertThat(asyncCache.putIfAbsent("alpha", "other").get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .contains("one");

            CountingSupplier supplier = new CountingSupplier("two");
            assertThat(asyncCache.get("beta", String.class, supplier).get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isEqualTo("two");
            assertThat(supplier.invocations()).isEqualTo(1);
            assertThat(asyncCache.invalidate("alpha").get(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(asyncCache.get("alpha", String.class).get(WAIT_SECONDS, TimeUnit.SECONDS)).isEmpty();
            assertThat(asyncCache.invalidateAll().get(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
            assertThat(asyncCache.get("beta", String.class).get(WAIT_SECONDS, TimeUnit.SECONDS)).isEmpty();
        }
    }

    @Test
    void cacheInformationReportsConfiguredPolicyAndStatistics() throws Exception {
        try (ApplicationContext context = configuredContext()) {
            SyncCache<?> cache = context.getBean(CacheManager.class).getCache("catalog");
            assertThat(cache.get("absent", String.class)).isEmpty();
            cache.put("present", "value");
            assertThat(cache.get("present", String.class)).contains("value");

            CacheInfo cacheInfo = awaitSingle(cache.getCacheInfo());
            assertThat(cacheInfo.getName()).isEqualTo("catalog");
            Map<String, Object> information = cacheInfo.get();
            assertThat(information.get("implementationClass")).isInstanceOf(String.class);

            Map<?, ?> caffeine = asMap(information.get("caffeine"));
            assertThat(caffeine.get("maximumSize")).isEqualTo(2L);
            assertThat(caffeine.get("maximumWeight")).isNull();
            assertThat(caffeine.get("expireAfterAccess")).isEqualTo(Duration.ofMinutes(2).toMillis());
            assertThat(caffeine.get("expireAfterWrite")).isEqualTo(Duration.ofMinutes(5).toMillis());
            assertThat(caffeine.get("recordingStats")).isEqualTo(true);

            Map<?, ?> statistics = asMap(caffeine.get("stats"));
            assertThat(statistics.get("requestCount")).isEqualTo(2L);
            assertThat(statistics.get("hitCount")).isEqualTo(1L);
            assertThat(statistics.get("missCount")).isEqualTo(1L);
        }
    }

    @Test
    void cacheManagerCreatesAndRetainsDynamicCaches() {
        try (ApplicationContext context = ApplicationContext.run()) {
            CacheManager<?> manager = context.getBean(CacheManager.class);

            SyncCache<?> first = manager.getCache("runtime-first");
            SyncCache<?> same = manager.getCache("runtime-first");
            SyncCache<?> second = manager.getCache("runtime-second");

            assertThat(same).isSameAs(first);
            assertThat(second).isNotSameAs(first);
            assertThat(manager.getCacheNames()).contains("runtime-first", "runtime-second");
            first.put("key", "first-value");
            second.put("key", "second-value");
            assertThat(first.get("key", String.class)).contains("first-value");
            assertThat(second.get("key", String.class)).contains("second-value");
        }
    }

    @Test
    void weightedCachePublishesRemovalAndEvictionEvents() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            RecordingRemovalListener listener = new RecordingRemovalListener();
            context.registerSingleton(RemovalListener.class, listener, null, false);

            CaffeineCacheConfiguration configuration = new CaffeineCacheConfiguration(
                    "weighted", context.getBean(ApplicationConfiguration.class));
            configuration.setMaximumWeight(1L);
            configuration.setListenToRemovals(true);
            configuration.setListenToEvictions(true);
            configuration.setRecordStats(true);
            configuration.setTestMode(true);

            DefaultSyncCache cache = new DefaultSyncCache(
                    configuration, context, context.getBean(ConversionService.class));
            cache.put("first", "one");
            cache.put("second", "two");
            cache.getNativeCache().cleanUp();
            cache.invalidateAll();
            cache.getNativeCache().cleanUp();

            assertThat(listener.hasEviction()).isTrue();
            assertThat(listener.hasCause(RemovalCause.EXPLICIT)).isTrue();

            Map<?, ?> caffeine = asMap(awaitSingle(cache.getCacheInfo()).get().get("caffeine"));
            assertThat(caffeine.get("maximumSize")).isNull();
            assertThat(caffeine.get("maximumWeight")).isEqualTo(1L);
            assertThat((Long) caffeine.get("weightedSize")).isLessThanOrEqualTo(1L);
        }
    }

    @Test
    void namedWeigherControlsCacheAdmissionByValueWeight() {
        try (ApplicationContext context = ApplicationContext.run()) {
            RecordingWeigher weigher = new RecordingWeigher();
            context.registerSingleton(
                    Weigher.class, weigher, Qualifiers.byName("length-weighted"), false);

            CaffeineCacheConfiguration configuration = new CaffeineCacheConfiguration(
                    "length-weighted", context.getBean(ApplicationConfiguration.class));
            configuration.setMaximumWeight(5L);
            configuration.setTestMode(true);

            DefaultSyncCache cache = new DefaultSyncCache(
                    configuration, context, context.getBean(ConversionService.class));
            cache.put("oversized", "123456");
            cache.getNativeCache().cleanUp();
            assertThat(cache.get("oversized", String.class)).isEmpty();

            cache.put("accepted", "four");
            cache.getNativeCache().cleanUp();
            assertThat(cache.get("accepted", String.class)).contains("four");
            assertThat(weigher.values()).contains("123456", "four");
        }
    }

    private static ApplicationContext configuredContext() {
        Map<String, Object> properties = Map.of(
                "micronaut.caches.catalog.initial-capacity", 2,
                "micronaut.caches.catalog.maximum-size", 2,
                "micronaut.caches.catalog.expire-after-access", "2m",
                "micronaut.caches.catalog.expire-after-write", "5m",
                "micronaut.caches.catalog.record-stats", true,
                "micronaut.caches.catalog.test-mode", true);
        return ApplicationContext.run(properties);
    }

    private static CacheInfo awaitSingle(Publisher<CacheInfo> publisher) throws Exception {
        SingleValueSubscriber<CacheInfo> subscriber = new SingleValueSubscriber<>();
        publisher.subscribe(subscriber);
        return subscriber.value().get(WAIT_SECONDS, TimeUnit.SECONDS);
    }

    private static Map<?, ?> asMap(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<?, ?>) value;
    }

    private static final class CountingSupplier implements Supplier<String> {
        private final String value;
        private int invocations;

        private CountingSupplier(String value) {
            this.value = value;
        }

        @Override
        public String get() {
            invocations++;
            return value;
        }

        private int invocations() {
            return invocations;
        }
    }

    private static final class RecordingWeigher implements Weigher<Object, Object> {
        private final List<Object> values = new ArrayList<>();

        @Override
        public int weigh(Object key, Object value) {
            values.add(value);
            return value.toString().length();
        }

        private List<Object> values() {
            return values;
        }
    }

    private static final class RecordingRemovalListener implements RemovalListener<Object, Object> {
        private final List<RemovalCause> causes = new ArrayList<>();

        @Override
        public void onRemoval(Object key, Object value, RemovalCause cause) {
            causes.add(cause);
        }

        private boolean hasEviction() {
            for (RemovalCause cause : causes) {
                if (cause.wasEvicted()) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasCause(RemovalCause expected) {
            return causes.contains(expected);
        }
    }

    private static final class SingleValueSubscriber<T> implements Subscriber<T> {
        private final CompletableFuture<T> value = new CompletableFuture<>();

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(T item) {
            value.complete(item);
        }

        @Override
        public void onError(Throwable throwable) {
            value.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            value.complete(null);
        }

        private CompletableFuture<T> value() {
            return value;
        }
    }
}
