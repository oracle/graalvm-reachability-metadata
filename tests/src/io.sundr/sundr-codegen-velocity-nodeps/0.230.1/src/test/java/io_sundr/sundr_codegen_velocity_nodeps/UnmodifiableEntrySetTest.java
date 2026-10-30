/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.sundr.deps.org.apache.commons.collections.map.UnmodifiableMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class UnmodifiableEntrySetTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Map backing = new HashMap<>();
        backing.put("key", "value");
        Map map = UnmodifiableMap.decorate(backing);
        Map.Entry[] entries = (Map.Entry[]) map.entrySet().toArray(new Map.Entry[2]);
        assertEquals("value", entries[0].getValue());
        assertNull(entries[1]);
        assertThrows(UnsupportedOperationException.class, () -> entries[0].setValue("changed"));
        assertEquals("value", map.get("key"));
    }
}
