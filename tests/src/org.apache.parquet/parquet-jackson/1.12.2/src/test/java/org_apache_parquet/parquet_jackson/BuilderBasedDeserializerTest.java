/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import shaded.parquet.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public class BuilderBasedDeserializerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void invokesBuilderBuildMethodAfterDeserializingProperties() throws Exception {
        BuildableMessage message = MAPPER.readValue("""
                {
                  "name": "daily-summary",
                  "priority": 5,
                  "labels": ["parquet", "jackson"]
                }
                """, BuildableMessage.class);

        assertThat(message.getName()).isEqualTo("daily-summary");
        assertThat(message.getPriority()).isEqualTo(5);
        assertThat(message.getLabels()).containsExactly("parquet", "jackson");
        assertThat(message.isBuiltByBuilder()).isTrue();
    }

    @JsonDeserialize(builder = BuildableMessage.Builder.class)
    public static final class BuildableMessage {
        private final String name;
        private final int priority;
        private final List<String> labels;
        private final boolean builtByBuilder;

        private BuildableMessage(Builder builder) {
            this.name = builder.name;
            this.priority = builder.priority;
            this.labels = List.copyOf(builder.labels);
            this.builtByBuilder = builder.buildInvoked;
        }

        public String getName() {
            return name;
        }

        public int getPriority() {
            return priority;
        }

        public List<String> getLabels() {
            return labels;
        }

        public boolean isBuiltByBuilder() {
            return builtByBuilder;
        }

        @JsonPOJOBuilder(withPrefix = "with")
        public static final class Builder {
            private String name;
            private int priority;
            private List<String> labels = new ArrayList<>();
            private boolean buildInvoked;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withPriority(int priority) {
                this.priority = priority;
                return this;
            }

            public Builder withLabels(List<String> labels) {
                this.labels = new ArrayList<>(labels);
                return this;
            }

            public BuildableMessage build() {
                this.buildInvoked = true;
                return new BuildableMessage(this);
            }
        }
    }
}
