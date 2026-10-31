/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JacksonInject;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonValue;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.InjectableValues;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedFieldTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setInjectableValues(new InjectableValues.Std().addValue("tenant", "north"));
        Tenant tenant = mapper.readValue("""
                {}
                """, Tenant.class);
        assertEquals("north", tenant.name);
        assertEquals("\"north\"", mapper.writeValueAsString(tenant));
    }

    @Test
    void restoresConfiguredReaderWithFieldProperties() throws Exception {
        ObjectReader reader = new ObjectMapper().readerFor(Document.class);
        ObjectReader restored = SerializationSupport.roundTrip(reader);
        Document document = restored.readValue("""
                {"title":"Guide","pages":17}
                """);
        assertEquals("Guide", document.title);
        assertEquals(17, document.pages);
    }

    public static class Document {
        public String title;
        public int pages;
    }

    public static class Tenant {
        @JacksonInject("tenant")
        @JsonValue
        public String name;
    }
}
