/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonBeanPropertyWriterTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializerReadsValuesThroughGetterAndFieldAccessors() throws Exception {
        assertThat(mapper.writeValueAsString(new MethodBackedBean("method"))).isEqualTo("{\"value\":\"method\"}");
        assertThat(mapper.writeValueAsString(new FieldBackedBean("field"))).isEqualTo("{\"value\":\"field\"}");
    }

    static class MethodBackedBean {

        private final String value;

        MethodBackedBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    static class FieldBackedBean {

        public String value;

        FieldBackedBean(String value) {
            this.value = value;
        }
    }
}
