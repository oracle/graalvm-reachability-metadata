/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.jctools.maps.NonBlockingHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonBlockingHashMapTest {
    @Test
    void preservesContents() throws Exception {
        NonBlockingHashMap<String, Integer> original = new NonBlockingHashMap<>();
        original.put("alpha", 4);
        original.put("beta", 8);
        NonBlockingHashMap<String, Integer> restored = SerializationSupport.roundTrip(original);
        assertEquals(Map.of("alpha", 4, "beta", 8), restored);
        assertTrue(restored.replace("alpha", 4, 6));
        assertEquals(6, restored.get("alpha"));
    }
}
