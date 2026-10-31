/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.base.Supplier;
import com.couchbase.client.core.deps.com.google.common.collect.Multimap;
import com.couchbase.client.core.deps.com.google.common.collect.Multimaps;
import java.io.Serializable;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MultimapsInnerCustomSortedSetMultimapTest {
    @Test
    void preservesContents() throws Exception {
        Multimap<String, String> original = Multimaps.newSortedSetMultimap(new HashMap<>(), new Values());
        original.put("group", "alpha");
        original.put("group", "beta");
        Multimap<String, String> restored = SerializationSupport.roundTrip(original);
        assertEquals(original, restored);
        restored.put("next", "gamma");
        assertTrue(restored.containsEntry("next", "gamma"));
        assertFalse(original.containsKey("next"));
    }

    public static class Values implements Supplier<SortedSet<String>>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public SortedSet<String> get() {
            return new TreeSet<>();
        }
    }
}
