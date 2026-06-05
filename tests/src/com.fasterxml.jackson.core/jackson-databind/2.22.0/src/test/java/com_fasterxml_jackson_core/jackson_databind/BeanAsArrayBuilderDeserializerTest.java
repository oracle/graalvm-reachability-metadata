/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanAsArrayBuilderDeserializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void deserializesArrayShapedValueWithPojoBuilder() throws JsonProcessingException {
        ArrayShapedBuiltBean bean = MAPPER.readValue("""
                ["arabica", 7]
                """, ArrayShapedBuiltBean.class);

        assertThat(bean.getName()).isEqualTo("arabica");
        assertThat(bean.getAmount()).isEqualTo(7);
    }

    @JsonDeserialize(builder = ArrayShapedBuiltBean.Builder.class)
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"name", "amount"})
    public static final class ArrayShapedBuiltBean {
        private final String name;
        private final int amount;

        private ArrayShapedBuiltBean(Builder builder) {
            this.name = builder.name;
            this.amount = builder.amount;
        }

        public String getName() {
            return name;
        }

        public int getAmount() {
            return amount;
        }

        @JsonPOJOBuilder(withPrefix = "")
        @JsonFormat(shape = JsonFormat.Shape.ARRAY)
        @JsonPropertyOrder({"name", "amount"})
        public static final class Builder {
            private String name;
            private int amount;

            public Builder name(String name) {
                this.name = name;
                return this;
            }

            public Builder amount(int amount) {
                this.amount = amount;
                return this;
            }

            public ArrayShapedBuiltBean build() {
                return new ArrayShapedBuiltBean(this);
            }
        }
    }
}
