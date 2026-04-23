/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonBeanAsArrayBuilderDeserializerTest {

    @Test
    void beanAsArrayBuilderDeserializerInvokesBuildMethod() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ArrayBuiltValue value = mapper.readValue("[\"array\"]", ArrayBuiltValue.class);
        assertThat(value.value).isEqualTo("array");
    }

    @JsonDeserialize(builder = ArrayBuiltValueBuilder.class)
    static class ArrayBuiltValue {

        final String value;

        ArrayBuiltValue(String value) {
            this.value = value;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public static class ArrayBuiltValueBuilder {

        private String value;

        public ArrayBuiltValueBuilder withValue(String value) {
            this.value = value;
            return this;
        }

        public ArrayBuiltValue build() {
            return new ArrayBuiltValue(value);
        }
    }
}
