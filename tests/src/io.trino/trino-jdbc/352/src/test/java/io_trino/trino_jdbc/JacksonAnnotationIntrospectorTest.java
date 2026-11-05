/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino.trino_jdbc;

import io.trino.jdbc.$internal.jackson.annotation.JsonAlias;
import io.trino.jdbc.$internal.jackson.annotation.JsonEnumDefaultValue;
import io.trino.jdbc.$internal.jackson.annotation.JsonProperty;
import io.trino.jdbc.$internal.jackson.databind.DeserializationFeature;
import io.trino.jdbc.$internal.jackson.databind.ObjectMapper;
import io.trino.jdbc.$internal.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonAnnotationIntrospectorTest {
    @Test
    void resolvesIndividualEnumNamesThroughAnnotationIntrospector() {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        assertEquals("ready", introspector.findEnumValue(State.READY));
        assertEquals("UNKNOWN", introspector.findEnumValue(State.UNKNOWN));
    }

    @Test
    void honorsEnumNamesAliasesAndDefault() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE);
        assertEquals(State.READY, mapper.readValue("\"available\"", State.class));
        assertEquals(State.READY, mapper.readValue("\"ready\"", State.class));
        assertEquals(State.UNKNOWN, mapper.readValue("\"unrecognized\"", State.class));
        assertEquals("\"ready\"", mapper.writeValueAsString(State.READY));
    }

    public enum State {
        @JsonProperty("ready")
        @JsonAlias("available")
        READY,
        @JsonEnumDefaultValue
        UNKNOWN
    }
}
