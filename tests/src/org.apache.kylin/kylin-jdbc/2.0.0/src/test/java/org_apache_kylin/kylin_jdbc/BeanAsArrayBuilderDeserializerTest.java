/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonFormat;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.kylin.jdbc.shaded.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanAsArrayBuilderDeserializerTest {
    @Test
    void buildsAPropertyOrderedBeanFromAnArray() throws Exception {
        ArrayValue value = new ObjectMapper().readValue("[\"sales\",9]", ArrayValue.class);

        assertThat(value.project).isEqualTo("sales");
        assertThat(value.limit).isEqualTo(9);
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"project", "limit"})
    @JsonDeserialize(builder = Builder.class)
    public static class ArrayValue {
        private final String project;
        private final int limit;

        private ArrayValue(String project, int limit) {
            this.project = project;
            this.limit = limit;
        }
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"project", "limit"})
    @JsonPOJOBuilder(withPrefix = "with")
    public static class Builder {
        private String project;
        private int limit;

        public Builder withProject(String project) {
            this.project = project;
            return this;
        }

        public Builder withLimit(int limit) {
            this.limit = limit;
            return this;
        }

        public ArrayValue build() {
            return new ArrayValue(project, limit);
        }
    }
}
