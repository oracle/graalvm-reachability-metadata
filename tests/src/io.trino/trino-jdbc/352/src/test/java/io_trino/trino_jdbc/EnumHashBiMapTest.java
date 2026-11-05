/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.EnumHashBiMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EnumHashBiMapTest {
    public enum Stage {
        QUEUED, RUNNING, COMPLETE
    }

    @Test
    void preservesEnumKeyTypeNullValueAndBijection() throws Exception {
        EnumHashBiMap<Stage, String> original = EnumHashBiMap.create(Stage.class);
        original.put(Stage.QUEUED, "waiting");
        original.put(Stage.COMPLETE, null);

        EnumHashBiMap<Stage, String> restored = SerializationRoundTrip.copy(original);

        assertNotSame(original, restored);
        assertEquals(original, restored);
        assertSame(Stage.class, restored.keyType());
        assertSame(Stage.COMPLETE, restored.inverse().get(null));
        assertThrows(IllegalArgumentException.class, () -> restored.put(Stage.RUNNING, "waiting"));
        restored.forcePut(Stage.RUNNING, "waiting");
        assertFalse(restored.containsKey(Stage.QUEUED));
        assertSame(Stage.RUNNING, restored.inverse().get("waiting"));
        assertEquals("waiting", original.get(Stage.QUEUED));
        assertFalse(original.containsKey(Stage.RUNNING));
    }
}
