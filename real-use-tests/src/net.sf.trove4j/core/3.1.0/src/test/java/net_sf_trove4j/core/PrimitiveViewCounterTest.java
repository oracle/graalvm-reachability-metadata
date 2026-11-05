/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.map.hash.TLongIntHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Trove's primitive-key map subsystem to build a product-view ranking.
public class PrimitiveViewCounterTest {
    @Test
    @Timeout(60)
    public void ranksProductsFromAggregatedViewEvents() {
        TLongIntHashMap viewsByProduct = new TLongIntHashMap();
        recordView(viewsByProduct, 1001L);
        recordView(viewsByProduct, 1002L);
        recordView(viewsByProduct, 1001L);
        recordView(viewsByProduct, 1003L);
        recordView(viewsByProduct, 1002L);
        recordView(viewsByProduct, 1001L);

        List<ProductViews> ranking = new ArrayList<>();
        TLongIntIterator entries = viewsByProduct.iterator();
        while (entries.hasNext()) {
            entries.advance();
            ranking.add(new ProductViews(entries.key(), entries.value()));
        }
        ranking.sort(Comparator.comparingInt(ProductViews::views)
                .reversed()
                .thenComparingLong(ProductViews::productId));

        assertEquals(3, viewsByProduct.size());
        assertEquals(List.of(
                new ProductViews(1001L, 3),
                new ProductViews(1002L, 2),
                new ProductViews(1003L, 1)), ranking);
    }

    private static void recordView(TLongIntHashMap viewsByProduct, long productId) {
        viewsByProduct.adjustOrPutValue(productId, 1, 1);
    }

    private record ProductViews(long productId, int views) {
    }
}
