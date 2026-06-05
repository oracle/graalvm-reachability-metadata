/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.beans.ConstructorProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonConstructorPropertiesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializeConstructorProperties() throws JsonProcessingException {
        Foo foo = MAPPER.readValue("{ \"bar\": \"baz\" }", Foo.class);
        assertThat(foo.getBar()).isEqualTo("baz");
    }

    public static final class Foo {

        private final String bar;

        @ConstructorProperties("bar")
        public Foo(String bar) {
            this.bar = bar;
        }

        public String getBar() {
            return bar;
        }
    }

}
