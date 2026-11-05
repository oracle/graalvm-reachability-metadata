/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JacksonInject;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonCreator;
import com.couchbase.client.core.deps.com.fasterxml.jackson.annotation.JsonValue;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.InjectableValues;
import com.couchbase.client.core.deps.com.fasterxml.jackson.databind.ObjectMapper;
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
