/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.core.type.TypeReference;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StdKeyDeserializerInnerStringCtorKeyDeserializerTest {
    @Test
    void constructsMapKeysFromPropertyNames() throws Exception {
        Map<Key, Integer> values = new ObjectMapper().readValue("{\"alpha\":7}", new TypeReference<Map<Key, Integer>>() { });
        assertEquals(1, values.size());
        assertEquals("alpha", values.keySet().iterator().next().text);
        assertEquals(7, values.values().iterator().next());
    }

    public static class Key {
        public final String text;

        public Key(String text) {
            this.text = text;
        }
    }
}
