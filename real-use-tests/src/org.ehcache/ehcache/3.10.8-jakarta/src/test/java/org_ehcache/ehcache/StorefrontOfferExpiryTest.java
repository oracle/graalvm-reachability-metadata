/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_ehcache.ehcache;

import org.ehcache.Cache;
import org.ehcache.CacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.expiry.ExpiryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Exercises Ehcache's custom expiry policy subsystem for storefront offer withdrawal.
 * A storefront keeps published offers until withdrawal and rejects unpublished offers without clock-based waits.
 */
public class StorefrontOfferExpiryTest {
    @Test
    @Timeout(60)
    public void removesWithdrawnOffersAndAllowsRepublication() {
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("offers", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, Offer.class, ResourcePoolsBuilder.heap(100))
                        .withExpiry(new PublicationExpiry()))
                .build(true)) {
            Cache<String, Offer> offers = manager.getCache("offers", String.class, Offer.class);
            Offer coffee = new Offer("Coffee subscription", 1299, true);
            Offer tea = new Offer("Tea selection", 799, true);
            offers.put("coffee", coffee);
            offers.put("tea", tea);

            // Unpublished feed entries must never become visible to shoppers.
            offers.put("cocoa", new Offer("Cocoa sampler", 599, false));
            assertNull(offers.get("cocoa"));
            assertFalse(offers.containsKey("cocoa"));
            assertEquals(coffee, offers.get("coffee"));
            assertEquals(coffee, offers.get("coffee"));
            assertEquals(tea, offers.get("tea"));

            // An update carrying a withdrawal expires the previous published offer immediately.
            offers.put("coffee", new Offer("Coffee subscription", 1299, false));
            assertNull(offers.get("coffee"));
            assertFalse(offers.containsKey("coffee"));
            assertEquals(tea, offers.get("tea"));

            Offer republishedCoffee = new Offer("Coffee subscription", 1099, true);
            offers.put("coffee", republishedCoffee);
            assertEquals(republishedCoffee, offers.get("coffee"));

            Offer revisedTea = new Offer("Tea selection", 749, true);
            offers.put("tea", revisedTea);
            assertEquals(revisedTea, offers.get("tea"));
            assertEquals(republishedCoffee, offers.get("coffee"));
            assertNull(offers.get("cocoa"));
        }
    }

    private record Offer(String description, int priceCents, boolean published) {
    }

    private static final class PublicationExpiry implements ExpiryPolicy<String, Offer> {
        @Override
        public Duration getExpiryForCreation(String key, Offer value) {
            return value.published() ? INFINITE : Duration.ZERO;
        }

        @Override
        public Duration getExpiryForAccess(String key, Supplier<? extends Offer> value) {
            return null;
        }

        @Override
        public Duration getExpiryForUpdate(String key, Supplier<? extends Offer> oldValue, Offer newValue) {
            return newValue.published() ? INFINITE : Duration.ZERO;
        }
    }
}
