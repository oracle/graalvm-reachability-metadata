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

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void usesValueOfForMapKeys() throws Exception {
        Map<Key, String> values = new ObjectMapper().readValue("{\"north\":\"zone\"}", new TypeReference<Map<Key, String>>() { });
        assertEquals("north", values.keySet().iterator().next().text);
        assertEquals("zone", values.values().iterator().next());
    }

    public static class Key {
        public final String text;

        private Key(String text, boolean normalized) {
            this.text = text;
        }

        public static Key valueOf(String text) {
            return new Key(text, true);
        }
    }
}
