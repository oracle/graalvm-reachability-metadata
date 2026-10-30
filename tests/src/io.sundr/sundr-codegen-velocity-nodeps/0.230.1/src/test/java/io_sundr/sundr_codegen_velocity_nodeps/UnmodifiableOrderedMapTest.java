/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sundr.deps.org.apache.commons.collections.map.ListOrderedMap;
import io.sundr.deps.org.apache.commons.collections.map.UnmodifiableOrderedMap;
import io.sundr.deps.org.apache.commons.lang.SerializationUtils;
import java.io.Serializable;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UnmodifiableOrderedMapTest {
    @Test
    void exercisesPublicApi() throws Exception {
        ListOrderedMap backing = new ListOrderedMap();
        backing.put("alpha", "one");
        backing.put("beta", "two");
        Map original = UnmodifiableOrderedMap.decorate(backing);
        Map restored = (Map) SerializationUtils.clone((Serializable) original);
        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertEquals("one", restored.get("alpha"));
        assertEquals("two", restored.get("beta"));
        assertThrows(UnsupportedOperationException.class, () -> restored.put("gamma", "three"));
    }
}
