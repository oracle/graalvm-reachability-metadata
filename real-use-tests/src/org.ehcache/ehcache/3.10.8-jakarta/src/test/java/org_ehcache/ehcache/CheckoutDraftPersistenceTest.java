/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's persistent disk storage subsystem for checkout drafts that survive application restarts.
 * A shop restores saved baskets after a clean shutdown, changes one draft, and removes a completed checkout.
 */
public class CheckoutDraftPersistenceTest {
    @TempDir
    Path cacheDirectory;

    @Test
    @Timeout(60)
    public void restoresDraftsAndPersistsChangesAcrossRestarts() {
        try (PersistentCacheManager manager = openDraftCache()) {
            Cache<String, String> drafts = manager.getCache("checkout-drafts", String.class, String.class);
            drafts.put("customer-alice", "coffee:2,tea:1");
            drafts.put("customer-bob", "cocoa:3");
            assertEquals("coffee:2,tea:1", drafts.get("customer-alice"));
            assertEquals("cocoa:3", drafts.get("customer-bob"));
        }

        try (PersistentCacheManager manager = openDraftCache()) {
            Cache<String, String> drafts = manager.getCache("checkout-drafts", String.class, String.class);
            assertEquals("coffee:2,tea:1", drafts.get("customer-alice"));
            assertEquals("cocoa:3", drafts.get("customer-bob"));

            drafts.put("customer-alice", "coffee:2,tea:2");
            drafts.remove("customer-bob");
            drafts.put("customer-carol", "biscuits:1");
        }

        try (PersistentCacheManager manager = openDraftCache()) {
            Cache<String, String> drafts = manager.getCache("checkout-drafts", String.class, String.class);
            assertEquals("coffee:2,tea:2", drafts.get("customer-alice"));
            assertNull(drafts.get("customer-bob"));
            assertEquals("biscuits:1", drafts.get("customer-carol"));
        }
    }

    private PersistentCacheManager openDraftCache() {
        return CacheManagerBuilder.newCacheManagerBuilder()
                .with(CacheManagerBuilder.persistence(cacheDirectory.toFile()))
                .withCache("checkout-drafts", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, String.class,
                        ResourcePoolsBuilder.heap(10).disk(10, MemoryUnit.MB, true)))
                .build(true);
    }
}
