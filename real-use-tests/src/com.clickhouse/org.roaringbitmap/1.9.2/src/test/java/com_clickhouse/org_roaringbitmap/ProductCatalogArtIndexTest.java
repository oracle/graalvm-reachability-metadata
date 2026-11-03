/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.org_roaringbitmap;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.roaringbitmap.ArrayContainer;
import org.roaringbitmap.art.Art;
import org.roaringbitmap.art.Containers;
import org.roaringbitmap.art.KeyIterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the adaptive radix tree subsystem to resolve catalog keys to product posting lists.
public class ProductCatalogArtIndexTest {
    @Test
    @Timeout(60)
    public void resolvesAndMaintainsProductPostingLists() {
        Containers postingLists = new Containers();
        ArrayContainer westernProducts = new ArrayContainer();
        westernProducts.add((char) 101);
        westernProducts.add((char) 103);
        long westernList = postingLists.addContainer(westernProducts);

        ArrayContainer easternProducts = new ArrayContainer();
        easternProducts.add((char) 202);
        easternProducts.add((char) 204);
        long easternList = postingLists.addContainer(easternProducts);

        Art catalogIndex = new Art();
        byte[] westernKey = key("WEST01");
        byte[] easternKey = key("EAST01");
        catalogIndex.insert(westernKey, westernList);
        catalogIndex.insert(easternKey, easternList);

        assertEquals(westernList, catalogIndex.findByKey(westernKey));
        assertEquals(easternList, catalogIndex.findByKey(easternKey));
        assertEquals(2L, catalogIndex.getKeySize());
        assertTrue(postingLists.getContainer(catalogIndex.findByKey(westernKey)).contains((char) 103));
        assertFalse(postingLists.getContainer(catalogIndex.findByKey(easternKey)).contains((char) 103));

        KeyIterator iterator = catalogIndex.iterator(postingLists);
        List<String> keys = new ArrayList<>();
        List<Long> listIndexes = new ArrayList<>();
        while (iterator.hasNext()) {
            keys.add(new String(iterator.next(), StandardCharsets.US_ASCII));
            listIndexes.add(iterator.currentContainerIdx());
        }

        assertEquals(List.of("EAST01", "WEST01"), keys);
        assertEquals(List.of(easternList, westernList), listIndexes);
        List<Integer> westernProductsObserved = new ArrayList<>();
        for (Character product : postingLists.getContainer(westernList)) {
            westernProductsObserved.add((int) product.charValue());
        }
        assertEquals(List.of(101, 103), westernProductsObserved);

        assertEquals(easternList, catalogIndex.remove(easternKey));
        assertEquals(-1L, catalogIndex.findByKey(easternKey));
        assertFalse(catalogIndex.isEmpty());
        assertEquals(1L, catalogIndex.getKeySize());
        assertEquals(2, postingLists.getContainerSize());
    }

    private static byte[] key(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
