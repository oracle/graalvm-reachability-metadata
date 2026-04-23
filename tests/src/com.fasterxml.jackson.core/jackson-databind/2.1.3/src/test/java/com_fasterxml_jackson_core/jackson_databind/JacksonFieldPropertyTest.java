/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonFieldPropertyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void beanFieldDeserializationUsesSet() throws Exception {
        FieldBean bean = mapper.readValue("{\"value\":\"field\"}", FieldBean.class);
        assertThat(bean.value).isEqualTo("field");
    }

    @Test
    void builderFieldDeserializationUsesSetAndReturn() throws Exception {
        BuiltFieldValue value = mapper.readValue("{\"value\":\"builder\"}", BuiltFieldValue.class);
        assertThat(value.value).isEqualTo("builder");
    }

    static class FieldBean {

        public String value;
    }

    @JsonDeserialize(builder = FieldBuilder.class)
    static class BuiltFieldValue {

        final String value;

        BuiltFieldValue(String value) {
            this.value = value;
        }
    }

    public static class FieldBuilder {

        public String value;

        public BuiltFieldValue build() {
            return new BuiltFieldValue(value);
        }
    }
}
