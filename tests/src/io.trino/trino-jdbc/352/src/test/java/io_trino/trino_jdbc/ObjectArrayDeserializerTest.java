/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.databind.DeserializationFeature;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectArrayDeserializerTest {
    @Test
    void acceptsSingleBeanAsTypedArray() throws Exception {
        ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        Item[] items = mapper.readValue("{\"name\":\"single\"}", Item[].class);
        assertEquals(1, items.length);
        assertEquals("single", items[0].name);
    }

    public static class Item {
        public String name;
    }
}
