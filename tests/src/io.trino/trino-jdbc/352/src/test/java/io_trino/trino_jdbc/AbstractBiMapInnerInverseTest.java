/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.guava.collect.BiMap;
import io.trino.jdbc.$internal.guava.collect.EnumHashBiMap;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractBiMapInnerInverseTest {
    public enum Direction {
        NORTH, SOUTH, EAST
    }

    @Test
    void restoresForwardAndInverseAsLinkedViews() throws Exception {
        BiMap<Direction, String> forward = EnumHashBiMap.create(Direction.class);
        forward.put(Direction.NORTH, "N");
        forward.put(Direction.SOUTH, "S");
        List<BiMap<?, ?>> graph = SerializationRoundTrip.copy(List.of(forward, forward.inverse()));
        BiMap<?, ?> restoredForward = graph.get(0);
        @SuppressWarnings("unchecked")
        BiMap<String, Direction> restoredInverse = (BiMap<String, Direction>) graph.get(1);

        assertSame(restoredInverse, restoredForward.inverse());
        assertSame(restoredForward, restoredInverse.inverse());
        assertEquals(Direction.NORTH, restoredInverse.get("N"));
        restoredInverse.put("E", Direction.EAST);
        assertEquals("E", restoredForward.get(Direction.EAST));
        restoredInverse.remove("S");
        assertFalse(restoredForward.containsKey(Direction.SOUTH));
        assertEquals(2, restoredForward.size());
        assertFalse(forward.containsKey(Direction.EAST));
        assertEquals("S", forward.get(Direction.SOUTH));
    }
}
