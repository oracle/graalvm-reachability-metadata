/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImmutableListMultimapTest {
    @Test
    void preservesContents() throws Exception {
        ImmutableListMultimap<String, Integer> original = ImmutableListMultimap.of("fruit", 3, "fruit", 7, "grain", 5);
        ImmutableListMultimap<String, Integer> restored = SerializationSupport.roundTrip(original);
        assertEquals(original, restored);
        assertEquals(2, restored.get("fruit").size());
        assertEquals("grain", restored.inverse().get(5).iterator().next());
        assertThrows(UnsupportedOperationException.class, () -> restored.put("other", 9));
    }
}
