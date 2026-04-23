/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonJsonValueSerializerTest {

    @Test
    void jsonValueSerializerInvokesAccessorWithAndWithoutTypeInformation() throws Exception {
        ObjectMapper plainMapper = new ObjectMapper();
        assertThat(plainMapper.writeValueAsString(new JsonValueBean("plain"))).isEqualTo("\"plain\"");

        ObjectMapper typedMapper = new ObjectMapper();
        typedMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        String typedJson = typedMapper.writeValueAsString((Object) new JsonValueBean("typed"));
        assertThat(typedJson).contains("typed");
    }

    static class JsonValueBean {

        private final String value;

        JsonValueBean(String value) {
            this.value = value;
        }

        @JsonValue
        public String asJson() {
            return value;
        }
    }
}
