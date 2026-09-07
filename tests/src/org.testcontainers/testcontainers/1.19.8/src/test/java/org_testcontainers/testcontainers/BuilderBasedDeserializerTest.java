/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class BuilderBasedDeserializerTest {
    @Test
    void finishesDeserializationThroughABuilderMethod() throws Exception {
        BuiltValue result = new ObjectMapper().readValue("{\"text\":\"built\"}", BuiltValue.class);

        assertThat(result.text).isEqualTo("built");
    }

    @JsonDeserialize(builder = Builder.class)
    public static class BuiltValue {
        private final String text;

        public BuiltValue(String text) {
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

        public BuiltValue build() {
            return new BuiltValue(text);
        }
    }
}
