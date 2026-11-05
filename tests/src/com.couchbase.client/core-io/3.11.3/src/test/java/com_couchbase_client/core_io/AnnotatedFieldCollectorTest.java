/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonProperty;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotatedFieldCollectorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.addMixIn(Contact.class, ContactMixin.class);
        Contact value = mapper.readValue("""
                {"display_name":"Ada","id":8}
                """, Contact.class);
        assertEquals("Ada", value.name);
        assertEquals(8, value.id);
        assertTrue(mapper.readTree(mapper.writeValueAsString(value)).has("display_name"));
    }

    public static class Entity {
        public int id;
    }

    public static class Contact extends Entity {
        public String name;
    }

    public static class ContactMixin {
        @JsonProperty("display_name")
        public String name;
    }
}
