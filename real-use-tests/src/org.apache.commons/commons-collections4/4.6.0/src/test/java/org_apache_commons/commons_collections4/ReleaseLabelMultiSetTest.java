/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.SortedMultiSet;
import org.apache.commons.collections4.multiset.TreeMultiSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the multiset subsystem for counting release labels in a deployment dashboard.
 */
public class ReleaseLabelMultiSetTest {
    @Test
    @Timeout(60)
    public void countsReleaseLabelsAndRemovesRetiredAssignments() {
        SortedMultiSet<String> labels = new TreeMultiSet<>();
        labels.add("canary", 2);
        labels.add("stable", 4);
        labels.add("preview", 1);
        labels.add("stable");

        assertEquals(8, labels.size());
        assertEquals(5, labels.getCount("stable"));
        assertEquals(2, labels.getCount("canary"));
        assertEquals(List.of("canary", "preview", "stable"), List.copyOf(labels.uniqueSet()));
        assertEquals("canary", labels.first());
        assertEquals("stable", labels.last());
        assertTrue(labels.contains("preview"));
        assertFalse(labels.contains("retired"));

        int previousStableCount = labels.setCount("stable", 2);
        assertEquals(5, previousStableCount);
        assertEquals(2, labels.getCount("stable"));
        assertEquals(5, labels.size());

        int previousCanaryCount = labels.remove("canary", 1);
        assertEquals(2, previousCanaryCount);
        assertEquals(1, labels.getCount("canary"));
        assertTrue(labels.remove("preview"));
        assertFalse(labels.contains("preview"));
        assertEquals(List.of("canary", "stable"), List.copyOf(labels.uniqueSet()));
    }
}
