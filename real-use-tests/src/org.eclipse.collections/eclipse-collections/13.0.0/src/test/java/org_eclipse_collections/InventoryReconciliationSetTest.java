/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Eclipse Collections' primitive set subsystem to reconcile orderable inventory.
public class InventoryReconciliationSetTest {
    @Test
    @Timeout(60)
    public void publishesAStableSetOfOrderableSkus() {
        MutableIntSet requestedSkus = IntSets.mutable.with(101, 205, 309, 412);
        MutableIntSet stockedSkus = IntSets.mutable.with(101, 205, 412, 518);
        MutableIntSet qualityHoldSkus = IntSets.mutable.with(205, 518);

        MutableIntSet orderableSkus = requestedSkus.intersect(stockedSkus)
                .difference(qualityHoldSkus);
        ImmutableIntSet publishedSnapshot = orderableSkus.toImmutable();
        ImmutableIntSet withNewRequest = publishedSnapshot.newWith(633);

        assertArrayEquals(new int[]{101, 412}, publishedSnapshot.toSortedArray());
        assertEquals(2, publishedSnapshot.size());
        assertTrue(publishedSnapshot.contains(101));
        assertTrue(publishedSnapshot.contains(412));
        assertFalse(publishedSnapshot.contains(205));
        assertFalse(publishedSnapshot.contains(518));

        assertArrayEquals(new int[]{101, 412, 633}, withNewRequest.toSortedArray());
        assertFalse(publishedSnapshot.contains(633));
        assertEquals(2, orderableSkus.size());
    }
}
