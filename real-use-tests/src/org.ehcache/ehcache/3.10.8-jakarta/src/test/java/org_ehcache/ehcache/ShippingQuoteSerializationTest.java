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
import org.ehcache.config.units.MemoryUnit;
import org.ehcache.spi.serialization.Serializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Ehcache's custom binary serialization subsystem for off-heap shipping quotes.
 * A checkout stores isolated quote snapshots and accepts a revised offer only when the previous offer matches.
 */
public class ShippingQuoteSerializationTest {
    @Test
    @Timeout(60)
    public void isolatesQuoteSnapshotsAndConditionallyAcceptsRevisions() {
        try (CacheManager manager = CacheManagerBuilder.newCacheManagerBuilder()
                .withCache("shipping-quotes", CacheConfigurationBuilder.newCacheConfigurationBuilder(
                        String.class, ShippingQuote.class,
                        ResourcePoolsBuilder.newResourcePoolsBuilder().offheap(4, MemoryUnit.MB))
                        .withValueSerializer(new ShippingQuoteSerializer()))
                .build(true)) {
            Cache<String, ShippingQuote> quotes = manager.getCache(
                    "shipping-quotes", String.class, ShippingQuote.class);

            ShippingQuote carrierOffer = new ShippingQuote(1299, 3);
            quotes.put("basket-alice", carrierOffer);
            quotes.put("basket-bob", new ShippingQuote(899, 5));

            // Reusing the carrier's mutable response must not alter the stored offer.
            carrierOffer.priceCents = 9999;
            assertQuote(quotes.get("basket-alice"), 1299, 3);

            ShippingQuote displayedOffer = quotes.get("basket-alice");
            assertNotNull(displayedOffer);
            displayedOffer.deliveryDays = 10;
            assertQuote(quotes.get("basket-alice"), 1299, 3);

            // Compare the serialized offer by content, not by Java object identity.
            assertTrue(quotes.replace("basket-alice", new ShippingQuote(1299, 3), new ShippingQuote(1099, 2)));
            assertFalse(quotes.replace("basket-alice", new ShippingQuote(1299, 3), new ShippingQuote(1199, 4)));
            assertQuote(quotes.get("basket-alice"), 1099, 2);
            assertQuote(quotes.get("basket-bob"), 899, 5);

            // A checkout consumes only the offer it actually accepted.
            assertFalse(quotes.remove("basket-alice", new ShippingQuote(1299, 3)));
            assertTrue(quotes.remove("basket-alice", new ShippingQuote(1099, 2)));
            assertNull(quotes.get("basket-alice"));
            assertQuote(quotes.get("basket-bob"), 899, 5);
        }
    }

    private static void assertQuote(ShippingQuote quote, int priceCents, int deliveryDays) {
        assertNotNull(quote);
        assertEquals(priceCents, quote.priceCents);
        assertEquals(deliveryDays, quote.deliveryDays);
    }

    private static final class ShippingQuote {
        private int priceCents;
        private int deliveryDays;

        private ShippingQuote(int priceCents, int deliveryDays) {
            this.priceCents = priceCents;
            this.deliveryDays = deliveryDays;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShippingQuote quote
                    && priceCents == quote.priceCents && deliveryDays == quote.deliveryDays;
        }

        @Override
        public int hashCode() {
            return Objects.hash(priceCents, deliveryDays);
        }
    }

    private static final class ShippingQuoteSerializer implements Serializer<ShippingQuote> {
        @Override
        public ByteBuffer serialize(ShippingQuote quote) {
            ByteBuffer buffer = ByteBuffer.allocate(2 * Integer.BYTES);
            buffer.putInt(quote.priceCents);
            buffer.putInt(quote.deliveryDays);
            return buffer.flip();
        }

        @Override
        public ShippingQuote read(ByteBuffer binary) {
            ByteBuffer buffer = binary.duplicate();
            return new ShippingQuote(buffer.getInt(), buffer.getInt());
        }

        @Override
        public boolean equals(ShippingQuote quote, ByteBuffer binary) {
            return quote.equals(read(binary));
        }
    }
}
