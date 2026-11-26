/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Policy;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class LocalCacheFactoryTest {
    private static final Duration LIFETIME = Duration.ofHours(1);
    private static final boolean[] FLAGS = {false, true};
    private static final String KEY = "key";
    private static final String VALUE = "value";

    @Test
    void buildsManualCachesForEveryFeatureCombination() {
        for (Features features : Features.all()) {
            if (features.refreshAfterWrite()) {
                continue;
            }
            List<RemovalCause> removals = new ArrayList<>();
            Cache<Object, Object> cache = features.newBuilder(removals).build();

            assertThat(cache.get(KEY, key -> VALUE)).as("%s", features).isSameAs(VALUE);
            assertCacheHonorsFeatures(cache, features, removals);
        }
    }

    @Test
    void buildsLoadingCachesForEveryFeatureCombination() {
        for (Features features : Features.all()) {
            List<RemovalCause> removals = new ArrayList<>();
            LoadingCache<Object, Object> cache = features.newBuilder(removals).build(key -> VALUE);

            assertThat(cache.get(KEY)).as("%s", features).isSameAs(VALUE);
            assertThat(cache.policy().refreshAfterWrite().isPresent()).as("%s", features)
                    .isEqualTo(features.refreshAfterWrite());
            assertCacheHonorsFeatures(cache, features, removals);
        }
    }

    @Test
    void buildsAsyncLoadingCachesForEveryStrongValueCombination() throws Exception {
        for (Features features : Features.all()) {
            if (features.weakValues()) {
                continue;
            }
            List<RemovalCause> removals = new ArrayList<>();
            AsyncLoadingCache<Object, Object> cache = features.newBuilder(removals).buildAsync(key -> VALUE);

            assertThat(cache.get(KEY).get(10, TimeUnit.SECONDS)).as("%s", features).isSameAs(VALUE);
            assertCacheHonorsFeatures(cache.synchronous(), features, removals);
        }
    }

    private static void assertCacheHonorsFeatures(Cache<Object, Object> cache, Features features,
            List<RemovalCause> removals) {
        assertThat(cache.getIfPresent(KEY)).as("%s", features).isSameAs(VALUE);

        Policy<Object, Object> policy = cache.policy();
        assertThat(policy.isRecordingStats()).as("%s", features).isEqualTo(features.recordStats());
        if (features.recordStats()) {
            assertThat(cache.stats().missCount()).as("%s", features).isEqualTo(1);
            assertThat(cache.stats().hitCount()).as("%s", features).isEqualTo(1);
        }
        assertThat(policy.eviction().isPresent()).as("%s", features).isEqualTo(features.bound() != Bound.NONE);
        assertThat(policy.expireAfterAccess().isPresent()).as("%s", features)
                .isEqualTo(features.expireAfterAccess());
        assertThat(policy.expireAfterWrite().isPresent()).as("%s", features)
                .isEqualTo(features.expireAfterWrite());

        cache.invalidate(KEY);
        assertThat(cache.getIfPresent(KEY)).as("%s", features).isNull();
        if (features.removalListener()) {
            assertThat(removals).as("%s", features).containsExactly(RemovalCause.EXPLICIT);
        }
    }

    private enum Bound {
        NONE, SIZE, WEIGHT
    }

    private record Features(boolean weakKeys, boolean weakValues, boolean removalListener, boolean recordStats,
            Bound bound, boolean expireAfterAccess, boolean expireAfterWrite, boolean refreshAfterWrite) {

        static List<Features> all() {
            List<Features> all = new ArrayList<>();
            for (boolean weakKeys : FLAGS) {
                for (boolean weakValues : FLAGS) {
                    for (boolean removalListener : FLAGS) {
                        for (boolean recordStats : FLAGS) {
                            for (Bound bound : Bound.values()) {
                                for (boolean expireAfterAccess : FLAGS) {
                                    for (boolean expireAfterWrite : FLAGS) {
                                        for (boolean refreshAfterWrite : FLAGS) {
                                            all.add(new Features(weakKeys, weakValues, removalListener, recordStats,
                                                    bound, expireAfterAccess, expireAfterWrite, refreshAfterWrite));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return all;
        }

        Caffeine<Object, Object> newBuilder(List<RemovalCause> removals) {
            Caffeine<Object, Object> builder = Caffeine.newBuilder().executor(Runnable::run);
            if (weakKeys) {
                builder.weakKeys();
            }
            if (weakValues) {
                builder.weakValues();
            }
            if (removalListener) {
                builder = builder.removalListener((Object key, Object value, RemovalCause cause) -> removals.add(cause));
            }
            if (recordStats) {
                builder.recordStats();
            }
            if (bound == Bound.SIZE) {
                builder.maximumSize(10);
            } else if (bound == Bound.WEIGHT) {
                builder = builder.maximumWeight(10).weigher((Object key, Object value) -> 1);
            }
            if (expireAfterAccess) {
                builder.expireAfterAccess(LIFETIME);
            }
            if (expireAfterWrite) {
                builder.expireAfterWrite(LIFETIME);
            }
            if (refreshAfterWrite) {
                builder.refreshAfterWrite(LIFETIME);
            }
            return builder;
        }
    }
}
