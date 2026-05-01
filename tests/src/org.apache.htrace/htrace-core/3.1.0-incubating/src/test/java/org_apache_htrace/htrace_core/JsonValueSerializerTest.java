/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.htrace.fasterxml.jackson.annotation.JsonTypeName;
import org.apache.htrace.fasterxml.jackson.annotation.JsonValue;
import org.apache.htrace.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonValueSerializerTest {
    @Test
    void serializesJsonValueAnnotatedMethodResult() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new JsonValueBean("plain-value"));

        assertThat(json).isEqualTo("\"plain-value\"");
    }

    @Test
    void serializesJsonValueAnnotatedMethodResultWithTypeInformation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(new TypedJsonValueBean("typed-value"));

        assertThat(json)
                .startsWith("[")
                .contains("json-value-bean")
                .contains("typed-value");
    }

    public static class JsonValueBean {
        private final String value;

        public JsonValueBean(String value) {
            this.value = value;
        }

        @JsonValue
        public String asJsonValue() {
            return value;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    @JsonTypeName("json-value-bean")
    public static class TypedJsonValueBean {
        private final String value;

        public TypedJsonValueBean(String value) {
            this.value = value;
        }

        @JsonValue
        public String asJsonValue() {
            return value;
        }
    }
}
