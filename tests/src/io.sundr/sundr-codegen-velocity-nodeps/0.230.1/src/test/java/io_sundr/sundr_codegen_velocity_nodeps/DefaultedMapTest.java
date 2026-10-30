/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import io.sundr.deps.org.apache.commons.collections.map.DefaultedMap;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

public class DefaultedMapTest {
    @Test
    void exercisesPublicApi() throws Exception {
        TreeMap<String, String> backing = new TreeMap<>();
        backing.put("alpha", "one");
        backing.put("beta", "two");
        Map original = new DefaultedMap("absent");
        original.putAll(backing);
        Map restored = (Map) SerializationUtils.clone((Serializable) original);
        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals("one", restored.get("alpha"));
        assertEquals("two", restored.get("beta"));
        assertEquals("absent", restored.get("missing"));
        assertFalse(restored.containsKey("missing"));
    }
}
