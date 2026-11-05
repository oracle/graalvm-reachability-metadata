/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections;

import org.eclipse.collections.api.bag.ImmutableBag;
import org.eclipse.collections.api.bag.MutableBag;
import org.eclipse.collections.api.factory.Bags;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.tuple.primitive.ObjectIntPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Eclipse Collections' bag subsystem to summarize customer support tags.
public class SupportTagFrequencyTest {
    @Test
    @Timeout(60)
    public void publishesTheMostFrequentTagsFromAnImmutableSnapshot() {
        MutableBag<String> submittedTags = Bags.mutable.with(
                "delivery", "refund", "delivery", "account", "delivery", "refund", "account",
                "delivery", "shipping");
        submittedTags.addOccurrences("delivery", 1);
        submittedTags.addOccurrences("shipping", 2);

        ImmutableBag<String> publishedSnapshot = submittedTags.toImmutable();
        ListIterable<ObjectIntPair<String>> topTags = publishedSnapshot.topOccurrences(2);

        assertEquals(12, publishedSnapshot.size());
        assertEquals(5, publishedSnapshot.occurrencesOf("delivery"));
        assertEquals(4, publishedSnapshot.sizeDistinct());
        assertEquals(5, topTags.getFirst().getTwo());
        assertEquals("delivery", topTags.getFirst().getOne());
        assertEquals(3, topTags.get(1).getTwo());
        assertEquals("shipping", topTags.get(1).getOne());
    }
}
