/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.jctools.maps.NonBlockingIdentityHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonBlockingIdentityHashMapTest {
    @Test
    void preservesContents() throws Exception {
        NonBlockingIdentityHashMap<String, Integer> original = new NonBlockingIdentityHashMap<>();
        String first = new String("same");
        String second = new String("same");
        original.put(first, 3);
        original.put(second, 7);
        NonBlockingIdentityHashMap<String, Integer> restored = SerializationSupport.roundTrip(original);
        assertEquals(2, restored.size());
        assertTrue(restored.containsValue(3));
        assertTrue(restored.containsValue(7));
        assertEquals(2, restored.keySet().size());
    }
}
