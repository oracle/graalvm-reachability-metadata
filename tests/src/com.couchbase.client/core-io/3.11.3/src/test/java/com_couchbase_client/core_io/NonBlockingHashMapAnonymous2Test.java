/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.org.jctools.maps.NonBlockingHashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NonBlockingHashMapAnonymous2Test {
    @Test
    void preservesContents() throws Exception {
        NonBlockingHashMap<String, String> map = new NonBlockingHashMap<>();
        map.put("a", "first");
        map.put("b", "second");
        String[] values = map.values().toArray(new String[0]);
        assertEquals(2, values.length);
        assertTrue(Set.of(values).containsAll(Set.of("first", "second")));
    }
}
