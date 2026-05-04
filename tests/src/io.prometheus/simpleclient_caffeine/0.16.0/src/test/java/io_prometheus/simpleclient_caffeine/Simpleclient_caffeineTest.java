/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_prometheus.simpleclient_caffeine;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.MetricFamilySamples.Sample;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.cache.caffeine.CacheMetricsCollector;
import org.junit.jupiter.api.Test;

public class Simpleclient_caffeineTest {
    @Test
    void collectReportsRequestAndSizeMetricsForManualCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .recordStats()
                .build();
        cache.put("alpha", "one");
        assertThat(cache.getIfPresent("alpha")).isEqualTo("one");
        assertThat(cache.getIfPresent("missing")).isNull();

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("manual", cache);

        List<MetricFamilySamples> samples = collector.collect();
        assertFamily(samples, "caffeine_cache_hit", Type.COUNTER, "Cache hit totals");
        assertFamily(samples, "caffeine_cache_miss", Type.COUNTER, "Cache miss totals");
        assertFamily(samples, "caffeine_cache_requests", Type.COUNTER, "Cache request totals, hits + misses");
        assertFamily(samples, "caffeine_cache_estimated_size", Type.GAUGE, "Estimated cache size");

        assertThat(metricValue(samples, "caffeine_cache_hit", "manual")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_miss", "manual")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_requests", "manual")).isEqualTo(2.0);
        assertThat(metricValue(samples, "caffeine_cache_eviction", "manual")).isEqualTo(0.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "manual")).isEqualTo(1.0);
        assertThat(family(samples, "caffeine_cache_loads").samples).isEmpty();
        assertThat(family(samples, "caffeine_cache_load_duration_seconds").samples).isEmpty();
    }

    @Test
    void collectReportsMetricsForMultipleCaches() {
        Cache<String, String> usersCache = Caffeine.newBuilder()
                .recordStats()
                .build();
        usersCache.put("alice", "admin");
        assertThat(usersCache.getIfPresent("alice")).isEqualTo("admin");

        Cache<String, String> sessionsCache = Caffeine.newBuilder()
                .recordStats()
                .build();
        sessionsCache.put("session-1", "alice");
        sessionsCache.put("session-2", "bob");
        assertThat(sessionsCache.getIfPresent("session-1")).isEqualTo("alice");
        assertThat(sessionsCache.getIfPresent("expired")).isNull();

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("users", usersCache);
        collector.addCache("sessions", sessionsCache);

        List<MetricFamilySamples> samples = collector.collect();
        assertThat(metricValue(samples, "caffeine_cache_hit", "users")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_miss", "users")).isEqualTo(0.0);
        assertThat(metricValue(samples, "caffeine_cache_requests", "users")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "users")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_hit", "sessions")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_miss", "sessions")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_requests", "sessions")).isEqualTo(2.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "sessions")).isEqualTo(2.0);
    }

    @Test
    void collectReportsLoadingCacheSuccessesFailuresAndDurationSummary() {
        AtomicInteger loadCount = new AtomicInteger();
        LoadingCache<String, String> cache = Caffeine.newBuilder()
                .recordStats()
                .build(key -> {
                    loadCount.incrementAndGet();
                    if ("failure".equals(key)) {
                        throw new IllegalStateException("intentional load failure");
                    }
                    return "loaded-" + key;
                });

        assertThat(cache.get("ok")).isEqualTo("loaded-ok");
        assertThat(cache.get("ok")).isEqualTo("loaded-ok");
        assertThatThrownBy(() -> cache.get("failure")).isInstanceOf(RuntimeException.class);
        assertThat(loadCount.get()).isEqualTo(2);

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("loading", cache);

        List<MetricFamilySamples> samples = collector.collect();
        assertFamily(samples, "caffeine_cache_load_failure", Type.COUNTER, "Cache load failures");
        assertFamily(samples, "caffeine_cache_loads", Type.COUNTER, "Cache loads: both success and failures");
        assertFamily(samples, "caffeine_cache_load_duration_seconds", Type.SUMMARY,
                "Cache load duration: both success and failures");

        assertThat(metricValue(samples, "caffeine_cache_hit", "loading")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_miss", "loading")).isEqualTo(2.0);
        assertThat(metricValue(samples, "caffeine_cache_requests", "loading")).isEqualTo(3.0);
        assertThat(metricValue(samples, "caffeine_cache_load_failure", "loading")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_loads", "loading")).isEqualTo(2.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "loading")).isEqualTo(1.0);
        assertThat(summaryValue(samples, "caffeine_cache_load_duration_seconds_count", "loading")).isEqualTo(2.0);
        assertThat(summaryValue(samples, "caffeine_cache_load_duration_seconds_sum", "loading"))
                .isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void collectReportsMetricsForAsyncCacheAddedThroughAsyncApi() throws Exception {
        AsyncLoadingCache<String, String> cache = Caffeine.newBuilder()
                .recordStats()
                .executor(Runnable::run)
                .buildAsync(key -> "async-" + key);

        assertThat(cache.get("value").get(1, SECONDS)).isEqualTo("async-value");
        assertThat(cache.get("value").get(1, SECONDS)).isEqualTo("async-value");

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("async", cache);

        List<MetricFamilySamples> samples = collector.collect();
        assertThat(metricValue(samples, "caffeine_cache_hit", "async")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_miss", "async")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_requests", "async")).isEqualTo(2.0);
        assertThat(metricValue(samples, "caffeine_cache_loads", "async")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_load_failure", "async")).isEqualTo(0.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "async")).isEqualTo(1.0);
        assertThat(summaryValue(samples, "caffeine_cache_load_duration_seconds_count", "async")).isEqualTo(1.0);
    }

    @Test
    void collectReportsMetricsForManualAsyncCacheWithoutLoadSamples() throws Exception {
        AsyncCache<String, String> cache = Caffeine.newBuilder()
                .recordStats()
                .executor(Runnable::run)
                .buildAsync();
        cache.put("ready", CompletableFuture.completedFuture("async-ready"));

        assertThat(cache.getIfPresent("ready").get(1, SECONDS)).isEqualTo("async-ready");
        assertThat(cache.getIfPresent("missing")).isNull();

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("manual-async", cache);

        List<MetricFamilySamples> samples = collector.collect();
        assertThat(metricValue(samples, "caffeine_cache_hit", "manual-async")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_miss", "manual-async")).isEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_requests", "manual-async")).isEqualTo(2.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "manual-async")).isEqualTo(1.0);
        assertThat(family(samples, "caffeine_cache_load_failure").samples).isEmpty();
        assertThat(family(samples, "caffeine_cache_loads").samples).isEmpty();
        assertThat(family(samples, "caffeine_cache_load_duration_seconds").samples).isEmpty();
    }

    @Test
    void collectReportsEvictionWeightForWeightedCache() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .recordStats()
                .maximumWeight(1)
                .weigher((String key, String value) -> 1)
                .build();
        cache.put("first", "one");
        cache.put("second", "two");
        cache.cleanUp();

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("weighted", cache);

        List<MetricFamilySamples> samples = collector.collect();
        assertThat(metricValue(samples, "caffeine_cache_eviction", "weighted")).isGreaterThanOrEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_eviction_weight", "weighted")).isGreaterThanOrEqualTo(1.0);
        assertThat(metricValue(samples, "caffeine_cache_estimated_size", "weighted")).isLessThanOrEqualTo(1.0);
    }

    @Test
    void removeCacheAndClearStopPublishingCacheSamples() {
        Cache<String, String> cache = Caffeine.newBuilder()
                .recordStats()
                .build();
        cache.put("key", "value");

        CacheMetricsCollector collector = new CacheMetricsCollector();
        collector.addCache("removed", cache);

        assertThat(collector.removeCache("removed")).isSameAs(cache);
        assertThat(collector.removeCache("missing")).isNull();
        assertAllFamiliesEmpty(collector.collect());

        collector.addCache("cleared", cache);
        assertThat(metricValue(collector.collect(), "caffeine_cache_estimated_size", "cleared")).isEqualTo(1.0);

        collector.clear();
        assertAllFamiliesEmpty(collector.collect());
    }

    private static void assertFamily(List<MetricFamilySamples> samples, String name, Type type, String help) {
        MetricFamilySamples family = family(samples, name);
        assertThat(family.type).isEqualTo(type);
        assertThat(family.help).isEqualTo(help);
    }

    private static double metricValue(List<MetricFamilySamples> samples, String familyName, String cacheName) {
        List<Sample> matchingSamples = family(samples, familyName).samples.stream()
                .filter(sample -> sample.labelNames.equals(List.of("cache")))
                .filter(sample -> sample.labelValues.equals(List.of(cacheName)))
                .toList();
        assertThat(matchingSamples)
                .describedAs("samples for metric family %s and cache %s", familyName, cacheName)
                .hasSize(1);
        return matchingSamples.get(0).value;
    }

    private static double summaryValue(List<MetricFamilySamples> samples, String sampleName, String cacheName) {
        List<Sample> matchingSamples = family(samples, "caffeine_cache_load_duration_seconds").samples.stream()
                .filter(sample -> sample.name.equals(sampleName))
                .filter(sample -> sample.labelNames.equals(List.of("cache")))
                .filter(sample -> sample.labelValues.equals(List.of(cacheName)))
                .toList();
        assertThat(matchingSamples)
                .describedAs("summary samples named %s for cache %s", sampleName, cacheName)
                .hasSize(1);
        return matchingSamples.get(0).value;
    }

    private static MetricFamilySamples family(List<MetricFamilySamples> samples, String name) {
        List<MetricFamilySamples> matchingFamilies = samples.stream()
                .filter(sample -> sample.name.equals(name))
                .toList();
        assertThat(matchingFamilies)
                .describedAs("metric family %s", name)
                .hasSize(1);
        return matchingFamilies.get(0);
    }

    private static void assertAllFamiliesEmpty(List<MetricFamilySamples> samples) {
        assertThat(samples)
                .extracting(sample -> sample.samples)
                .allSatisfy(familySamples -> assertThat(familySamples).isEmpty());
    }
}
