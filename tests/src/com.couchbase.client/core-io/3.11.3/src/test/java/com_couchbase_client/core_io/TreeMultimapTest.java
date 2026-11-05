/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.TreeMultimap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TreeMultimapTest {
    @Test
    void preservesContents() throws Exception {
        TreeMultimap<String, Integer> original = TreeMultimap.create();
        original.put("fruit", 3);
        original.put("fruit", 7);
        original.put("grain", 5);
        TreeMultimap<String, Integer> restored = SerializationSupport.roundTrip(original);
        assertEquals(original, restored);
        assertEquals(List.of(3, 7), List.copyOf(restored.get("fruit")));
        restored.put("fruit", 9);
        assertEquals(3, restored.get("fruit").size());
        assertEquals(2, original.get("fruit").size());
    }
}
