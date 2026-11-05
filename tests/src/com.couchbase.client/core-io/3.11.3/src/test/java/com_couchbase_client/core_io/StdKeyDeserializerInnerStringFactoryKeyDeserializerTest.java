/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.core.type.TypeReference;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StdKeyDeserializerInnerStringFactoryKeyDeserializerTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Map<Key, Integer> result = mapper.readValue("""
                {"invoice-7":42}
                """, new TypeReference<Map<Key, Integer>>() { });
        assertEquals(1, result.size());
        assertEquals("invoice-7", result.keySet().iterator().next().value);
        assertEquals(42, result.values().iterator().next());
    }

    public static class Key {
        private final String value;

        private Key(String value) {
            this.value = value;
        }

        public static Key valueOf(String value) {
            return new Key(value);
        }
    }
}
