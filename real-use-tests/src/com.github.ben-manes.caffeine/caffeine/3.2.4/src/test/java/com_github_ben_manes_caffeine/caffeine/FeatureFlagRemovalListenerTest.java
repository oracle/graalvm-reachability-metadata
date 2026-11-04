/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_ben_manes_caffeine.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.RemovalListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Caffeine's removal-listener subsystem for a feature-flag service.
 * Invalidating a retired flag removes it from the cache and records an audit event.
 */
public class FeatureFlagRemovalListenerTest {
    @Test
    @Timeout(60)
    public void recordsExplicitInvalidationWhileKeepingOtherFlagsAvailable() {
        List<RemovalEvent> auditTrail = new ArrayList<>();
        RemovalListener<String, FeatureFlag> listener = (key, value, cause) ->
                auditTrail.add(new RemovalEvent(key, value, cause));
        Cache<String, FeatureFlag> flags = Caffeine.newBuilder()
                .maximumSize(100)
                .removalListener(listener)
                .build();
        FeatureFlag checkout = new FeatureFlag("checkout", true);
        FeatureFlag search = new FeatureFlag("search", true);
        flags.put(checkout.name(), checkout);
        flags.put(search.name(), search);

        flags.invalidate(checkout.name());
        flags.cleanUp();

        assertNull(flags.getIfPresent(checkout.name()));
        assertEquals(search, flags.getIfPresent(search.name()));
        assertEquals(List.of(new RemovalEvent("checkout", checkout, RemovalCause.EXPLICIT)), auditTrail);
        assertEquals(1, flags.estimatedSize());
    }

    private record FeatureFlag(String name, boolean enabled) {
    }

    private record RemovalEvent(String key, FeatureFlag value, RemovalCause cause) {
    }
}
