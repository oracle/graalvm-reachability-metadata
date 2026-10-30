/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sundr.deps.org.apache.velocity.util.MapFactory;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class MapFactoryTest {
    @Test
    void exercisesPublicApi() throws Exception {
        Map map = MapFactory.create(16, 0.75f, 4, false);
        assertNull(map.put("key", "value"));
        assertEquals("value", map.get("key"));
        assertEquals("value", map.remove("key"));
        assertTrue(map.isEmpty());
    }
}
