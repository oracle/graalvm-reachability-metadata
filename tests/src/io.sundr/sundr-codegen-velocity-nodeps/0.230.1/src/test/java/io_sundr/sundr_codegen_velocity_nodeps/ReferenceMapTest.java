/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.sundr.deps.org.apache.commons.collections.ReferenceMap;
import org.junit.jupiter.api.Test;

public class ReferenceMapTest {
    @Test
    void retainsHardReferencesAndUpdatesEntries() {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD);
        map.put("alpha", "one");
        map.put("beta", "two");
        assertEquals("one", map.put("alpha", "updated"));
        assertEquals("updated", map.get("alpha"));
        assertEquals("two", map.remove("beta"));
        assertFalse(map.containsKey("beta"));
        assertEquals(1, map.size());
    }
}
