/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Exercises Caffeine's declarative cache-configuration subsystem for a feature service.
 * A deployment-provided specification builds a bounded cache with statistics enabled.
 */
public class DeclarativeCacheConfigurationTest {
    @Test
    @Timeout(60)
    public void buildsFeatureCacheFromDeploymentSpecification() {
        Cache<String, FeatureFlag> flags = Caffeine.from("maximumSize=2,recordStats").build();
        FeatureFlag checkout = new FeatureFlag("checkout", true);
        FeatureFlag search = new FeatureFlag("search", true);
        FeatureFlag billing = new FeatureFlag("billing", false);

        flags.put(checkout.name(), checkout);
        flags.put(search.name(), search);
        flags.put(billing.name(), billing);
        flags.cleanUp();

        assertEquals(2, flags.estimatedSize());
        assertEquals(billing, flags.getIfPresent(billing.name()));
        assertNotNull(flags.stats());
        assertEquals(1, flags.stats().hitCount());
    }

    private record FeatureFlag(String name, boolean enabled) {
    }
}
