/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonAlias;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonProperty;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonAnnotationIntrospectorTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertEquals("\"ready\"", mapper.writeValueAsString(Status.READY));
        assertEquals(Status.READY, mapper.readValue("\"available\"", Status.class));
    }

    @Test
    @SuppressWarnings("deprecation")
    void readsEnumNamesAndAliasesThroughIntrospectionApi() {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Status[] values = Status.values();
        String[] names = introspector.findEnumValues(Status.class, values,
                new String[]{"READY", "WAITING"});
        String[][] aliases = new String[values.length][];
        introspector.findEnumAliases(Status.class, values, aliases);
        assertArrayEquals(new String[]{"ready", "WAITING"}, names);
        assertArrayEquals(new String[]{"available", "ok"}, aliases[0]);
    }

    public enum Status {
        @JsonProperty("ready")
        @JsonAlias({"available", "ok"})
        READY,
        WAITING
    }
}
