/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.jctools.maps.NonBlockingHashMapLong;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NonBlockingHashMapLongTest {
    @Test
    void preservesContents() throws Exception {
        NonBlockingHashMapLong<String> original = new NonBlockingHashMapLong<>();
        original.put(0L, "zero");
        original.put(42L, "answer");
        NonBlockingHashMapLong<String> restored = SerializationSupport.roundTrip(original);
        assertEquals(Map.of(0L, "zero", 42L, "answer"), restored);
        assertEquals("answer", restored.remove(42L));
        assertEquals(1, restored.size());
    }
}
