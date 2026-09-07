/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanAsArrayBuilderDeserializerTest {
    @Test
    void finishesArrayShapedDeserializationThroughABuilder() throws Exception {
        ArrayBuiltValue result = new ObjectMapper().readValue("[\"built\"]", ArrayBuiltValue.class);

        assertThat(result.text).isEqualTo("built");
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder("text")
    @JsonDeserialize(builder = Builder.class)
    public static class ArrayBuiltValue {
        private final String text;

        public ArrayBuiltValue(String text) {
            this.text = text;
        }
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private String text;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public ArrayBuiltValue build() {
            return new ArrayBuiltValue(text);
        }
    }
}
