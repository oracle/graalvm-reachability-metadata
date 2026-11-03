/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.RangeBitmap;
import org.roaringbitmap.RoaringBitmap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises the RangeBitmap column-index subsystem to find catalog products by price.
public class ProductPriceRangeIndexTest {
    @Test
    @Timeout(60)
    public void findsAffordableProductsAndAppliesAnAvailabilityFilter() {
        long[] pricesInCents = {1299, 4999, 2499, 7999, 1999, 3499, 999, 5999};
        RangeBitmap.Appender priceIndexBuilder = RangeBitmap.appender(10_000);
        for (long price : pricesInCents) {
            priceIndexBuilder.add(price);
        }
        RangeBitmap priceIndex = priceIndexBuilder.build();

        RoaringBitmap availableProductIds = RoaringBitmap.bitmapOf(0, 2, 4, 6);
        RoaringBitmap midrangeProducts = priceIndex.between(1_000, 3_500);
        RoaringBitmap affordableAvailableProducts = priceIndex.lte(2_499, availableProductIds);

        assertArrayEquals(new int[] {0, 2, 4, 5}, midrangeProducts.toArray());
        assertArrayEquals(new int[] {0, 2, 4, 6}, affordableAvailableProducts.toArray());
        assertEquals(1L, priceIndex.eqCardinality(2_499));
        assertEquals(3L, priceIndex.betweenCardinality(1_000, 3_500, availableProductIds));
    }
}
