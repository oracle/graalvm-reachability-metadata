/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import static org.assertj.core.api.Assertions.assertThat;

import org.codehaus.jackson.annotate.JsonValue;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.jupiter.api.Test;

public class JsonValueSerializerTest {
    @Test
    public void serializesObjectUsingJsonValueAccessor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonValueBackedObject value = new JsonValueBackedObject("plain-value");

        String json = mapper.writeValueAsString(value);

        assertThat(json).isEqualTo("\"plain-value\"");
    }

    @Test
    public void serializesObjectWithTypeUsingJsonValueAccessor() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        DefaultTypedJsonValue value = new DefaultTypedJsonValue("typed-value");

        String json = mapper.writeValueAsString(value);

        assertThat(json)
                .contains(DefaultTypedJsonValue.class.getName())
                .contains("typed-value");
    }

    public static final class JsonValueBackedObject {
        private final String value;

        public JsonValueBackedObject(String value) {
            this.value = value;
        }

        @JsonValue
        public String asJsonValue() {
            return value;
        }
    }

    public static class DefaultTypedJsonValue {
        private final String value;

        public DefaultTypedJsonValue(String value) {
            this.value = value;
        }

        @JsonValue
        public String asJsonValue() {
            return value;
        }
    }
}
