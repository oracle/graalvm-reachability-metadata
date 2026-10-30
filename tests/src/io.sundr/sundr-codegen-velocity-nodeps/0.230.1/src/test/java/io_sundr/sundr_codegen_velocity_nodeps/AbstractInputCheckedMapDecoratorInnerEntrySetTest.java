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

import io.sundr.deps.org.apache.commons.collections.functors.NotNullPredicate;
import io.sundr.deps.org.apache.commons.collections.map.PredicatedMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class AbstractInputCheckedMapDecoratorInnerEntrySetTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Map map = PredicatedMap.decorate(new HashMap<>(), NotNullPredicate.INSTANCE, NotNullPredicate.INSTANCE);
        map.put("key", "value");
        Map.Entry[] entries = (Map.Entry[]) map.entrySet().toArray(new Map.Entry[2]);
        assertEquals("key", entries[0].getKey());
        assertNull(entries[1]);
        assertThrows(IllegalArgumentException.class, () -> entries[0].setValue(null));
        entries[0].setValue("updated");
        assertEquals("updated", map.get("key"));
    }
}
