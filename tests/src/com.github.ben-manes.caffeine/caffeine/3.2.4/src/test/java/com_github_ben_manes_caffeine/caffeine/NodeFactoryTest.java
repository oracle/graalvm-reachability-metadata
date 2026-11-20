/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class NodeFactoryTest {
    private static final Duration LIFETIME = Duration.ofHours(1);
    private static final boolean[] FLAGS = {false, true};
    private static final int WEIGHT = 3;
    private static final String KEY = "key";
    private static final String VALUE = "value";

    @Test
    void storesEntriesForEveryNodeFeatureCombination() {
        for (Features features : Features.all()) {
            LoadingCache<Object, Object> cache = features.newBuilder().build(key -> VALUE);

            cache.put(KEY, VALUE);
            cache.cleanUp();

            assertThat(cache.asMap()).as("%s", features).containsEntry(KEY, VALUE);
            assertThat(cache.estimatedSize()).as("%s", features).isEqualTo(1);
            assertEntryMetadataMatchesFeatures(cache.policy(), features);
        }
    }

    private static void assertEntryMetadataMatchesFeatures(Policy<Object, Object> policy, Features features) {
        assertThat(policy.eviction().isPresent()).as("%s", features).isEqualTo(features.bound() != Bound.NONE);
        policy.eviction().ifPresent(eviction -> {
            if (features.bound() == Bound.WEIGHT) {
                assertThat(eviction.weightOf(KEY)).as("%s", features).hasValue(WEIGHT);
                assertThat(eviction.weightedSize()).as("%s", features).hasValue(WEIGHT);
            } else {
                assertThat(eviction.weightOf(KEY)).as("%s", features).isEmpty();
            }
        });

        assertThat(policy.expireAfterAccess().isPresent()).as("%s", features)
                .isEqualTo(features.expireAfterAccess());
        policy.expireAfterAccess().ifPresent(expiration ->
                assertThat(expiration.ageOf(KEY)).as("%s", features).isPresent());

        assertThat(policy.expireAfterWrite().isPresent()).as("%s", features)
                .isEqualTo(features.expireAfterWrite());
        policy.expireAfterWrite().ifPresent(expiration ->
                assertThat(expiration.ageOf(KEY)).as("%s", features).isPresent());

        assertThat(policy.refreshAfterWrite().isPresent()).as("%s", features)
                .isEqualTo(features.refreshAfterWrite());
        policy.refreshAfterWrite().ifPresent(refresh ->
                assertThat(refresh.ageOf(KEY)).as("%s", features).isPresent());
    }

    private enum ValueStrength {
        STRONG, WEAK, SOFT
    }

    private enum Bound {
        NONE, SIZE, WEIGHT
    }

    private record Features(boolean weakKeys, ValueStrength valueStrength, Bound bound, boolean expireAfterAccess,
            boolean expireAfterWrite, boolean refreshAfterWrite) {

        static List<Features> all() {
            List<Features> all = new ArrayList<>();
            for (boolean weakKeys : FLAGS) {
                for (ValueStrength valueStrength : ValueStrength.values()) {
                    for (Bound bound : Bound.values()) {
                        for (boolean expireAfterAccess : FLAGS) {
                            for (boolean expireAfterWrite : FLAGS) {
                                for (boolean refreshAfterWrite : FLAGS) {
                                    all.add(new Features(weakKeys, valueStrength, bound, expireAfterAccess,
                                            expireAfterWrite, refreshAfterWrite));
                                }
                            }
                        }
                    }
                }
            }
            return all;
        }

        Caffeine<Object, Object> newBuilder() {
            Caffeine<Object, Object> builder = Caffeine.newBuilder().executor(Runnable::run);
            if (weakKeys) {
                builder.weakKeys();
            }
            if (valueStrength == ValueStrength.WEAK) {
                builder.weakValues();
            } else if (valueStrength == ValueStrength.SOFT) {
                builder.softValues();
            }
            if (bound == Bound.SIZE) {
                builder.maximumSize(10);
            } else if (bound == Bound.WEIGHT) {
                builder = builder.maximumWeight(10).weigher((Object key, Object value) -> WEIGHT);
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
