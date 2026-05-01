/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.annotate.JsonValue;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonValueSerializerTest {
    @Test
    void serializesValueReturnedByJsonValueMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        PlainJsonValueBean value = new PlainJsonValueBean("alpha");

        String json = mapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"plain-alpha\"");
    }

    @Test
    void serializesTypedValueReturnedByJsonValueMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        TypedJsonValueBean value = new TypedJsonValueBean("beta");

        String json = mapper.writeValueAsString(value);

        assertThat(json).isEqualTo("[\"" + TypedJsonValueBean.class.getName() + "\",\"typed-beta\"]");
    }

    public static class PlainJsonValueBean {
        private final String name;

        public PlainJsonValueBean(String name) {
            this.name = name;
        }

        @JsonValue
        public String asJsonValue() {
            return "plain-" + name;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
    public static class TypedJsonValueBean {
        private final String name;

        public TypedJsonValueBean(String name) {
            this.name = name;
        }

        @JsonValue
        public String asJsonValue() {
            return "typed-" + name;
        }
    }
}
