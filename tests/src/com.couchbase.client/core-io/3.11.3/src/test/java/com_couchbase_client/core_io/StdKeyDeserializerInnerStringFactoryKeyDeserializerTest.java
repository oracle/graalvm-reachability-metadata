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

    @Test
    void createsMapKeysUsingStringFactoryWithoutStringConstructor() throws Exception {
        Map<InvoiceKey, String> invoices = new ObjectMapper().readValue("""
                {"invoice-42":"paid"}
                """, new TypeReference<Map<InvoiceKey, String>>() { });
        assertEquals(1, invoices.size());
        assertEquals(42, invoices.keySet().iterator().next().number);
        assertEquals("paid", invoices.values().iterator().next());
    }

    public static class InvoiceKey {
        public final int number;

        private InvoiceKey(int number) {
            this.number = number;
        }

        public static InvoiceKey valueOf(String text) {
            return new InvoiceKey(Integer.parseInt(text.substring("invoice-".length())));
        }
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
