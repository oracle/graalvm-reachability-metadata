/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BuilderBasedDeserializerTest {
    @Test
    void finishesDeserializationThroughTheConfiguredBuilder() throws Exception {
        BuiltValue value = new ObjectMapper().readValue("{\"name\":\"cube\"}", BuiltValue.class);

        assertThat(value.name).isEqualTo("cube");
    }

    @JsonDeserialize(builder = Builder.class)
    public static class BuiltValue {
        private final String name;

        private BuiltValue(String name) {
            this.name = name;
        }
    }

    @JsonPOJOBuilder(withPrefix = "with")
    public static class Builder {
        private String name;

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public BuiltValue build() {
            return new BuiltValue(name);
        }
    }
}
