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

public class JacksonMethodPropertyTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void beanSetterDeserializationUsesSet() throws Exception {
        SetterBean bean = mapper.readValue("{\"value\":\"setter\"}", SetterBean.class);
        assertThat(bean.getValue()).isEqualTo("setter");
    }

    @Test
    void builderSetterDeserializationUsesSetAndReturn() throws Exception {
        BuiltMethodValue value = mapper.readValue("{\"value\":\"builder\"}", BuiltMethodValue.class);
        assertThat(value.value).isEqualTo("builder");
    }

    static class SetterBean {

        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    @JsonDeserialize(builder = MethodBuilder.class)
    static class BuiltMethodValue {

        final String value;

        BuiltMethodValue(String value) {
            this.value = value;
        }
    }

    public static class MethodBuilder {

        private String value;

        public MethodBuilder withValue(String value) {
            this.value = value;
            return this;
        }

        public BuiltMethodValue build() {
            return new BuiltMethodValue(value);
        }
    }
}
