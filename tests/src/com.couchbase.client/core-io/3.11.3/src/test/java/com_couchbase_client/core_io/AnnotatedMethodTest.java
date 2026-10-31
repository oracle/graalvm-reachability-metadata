/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JacksonInject;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonCreator;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonAnySetter;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonIgnore;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonValue;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.BeanDescription;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.InjectableValues;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectReader;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.introspect.AnnotatedMethod;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotatedMethodTest {
    @Test
    void mapsApplicationData() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setInjectableValues(new InjectableValues.Std().addValue("suffix", "-checked"));
        Token token = mapper.readValue("\"abc\"", Token.class);
        assertEquals("abc", token.text);
        assertEquals("\"abc\"", mapper.writeValueAsString(token));
        Receiver receiver = mapper.readValue("""
                {}
                """, Receiver.class);
        assertEquals("-checked", receiver.suffix);
    }

    @Test
    void usesZeroArgumentFactoryAndAnySetter() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Properties properties = mapper.readValue("""
                {"region":"west","tier":"gold"}
                """, Properties.class);
        assertEquals("created", properties.origin);
        assertEquals(Map.of("region", "west", "tier", "gold"), properties.values);
        assertEquals(Choice.READY, mapper.readValue("{}", Choice.class));
    }

    @Test
    void restoresReaderWithFactoryMethod() throws Exception {
        ObjectReader reader = new ObjectMapper().readerFor(Token.class);
        ObjectReader restored = (ObjectReader) NettySerializationSupport.throughStreams(reader);
        Token token = restored.readValue("\"restored\"");
        assertEquals("restored", token.value());
    }

    @Test
    void invokesJsonValueAccessorDiscoveredByMapper() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        BeanDescription description = mapper.getSerializationConfig()
                .introspect(mapper.constructType(Token.class));
        AnnotatedMethod accessor = (AnnotatedMethod) description.findJsonValueAccessor();
        assertEquals("inspected", accessor.callOn(Token.from("inspected")));
        assertEquals("value", accessor.getName());
    }

    public enum Choice {
        READY;

        @JsonCreator
        public static Choice create() {
            return READY;
        }
    }

    public static class Properties {
        @JsonIgnore
        public final String origin;
        @JsonIgnore
        public final Map<String, String> values = new LinkedHashMap<>();

        private Properties(String origin) {
            this.origin = origin;
        }

        @JsonCreator
        public static Properties create() {
            return new Properties("created");
        }

        @JsonAnySetter
        public void put(String key, String value) {
            values.put(key, value);
        }
    }

    public static class Token {
        private final String text;

        private Token(String text) {
            this.text = text;
        }

        @JsonCreator
        public static Token from(String text) {
            return new Token(text);
        }

        @JsonValue
        public String value() {
            return text;
        }
    }

    public static class Receiver {
        private String suffix;

        @JacksonInject("suffix")
        public void assign(String suffix) {
            this.suffix = suffix;
        }
    }
}
