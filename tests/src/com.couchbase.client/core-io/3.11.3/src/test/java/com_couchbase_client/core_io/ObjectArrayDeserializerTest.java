/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.DeserializationFeature;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ObjectArrayDeserializerTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        Item[] items = mapper.readValue("""
                {"name":"apple"}
                """, Item[].class);
        assertEquals(1, items.length);
        assertEquals("apple", items[0].name);
    }

    public static class Item {
        public String name;
    }
}
