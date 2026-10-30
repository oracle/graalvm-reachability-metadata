/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;

import io.sundr.deps.org.apache.commons.collections.map.MultiValueMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MultiValueMapTest {
    @Test
    void exercisesPublicApi() throws Exception {
        MultiValueMap map = new MultiValueMap();
        map.put("key", "one");
        map.put("key", "two");
        map.put("key", "one");
        assertEquals(List.of("one", "two", "one"), map.getCollection("key"));
        assertEquals(3, map.totalSize());
    }

    @Test
    void decoratesAnExistingMapWithDefaultValueLists() {
        Map<String, Object> backing = new HashMap<>();
        MultiValueMap map = MultiValueMap.decorate(backing);
        map.put("key", "first");
        map.put("key", "second");
        assertEquals(List.of("first", "second"), backing.get("key"));
        assertEquals("first", map.remove("key", "first"));
        assertEquals(List.of("second"), backing.get("key"));
    }
}
