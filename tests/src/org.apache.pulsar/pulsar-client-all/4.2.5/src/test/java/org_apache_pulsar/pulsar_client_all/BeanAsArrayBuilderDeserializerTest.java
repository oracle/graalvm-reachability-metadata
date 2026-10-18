/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;

public class BeanAsArrayBuilderDeserializerTest {

    @Test
    void finishesArrayShapedDeserializationThroughABuilder() throws Exception {
        final RoutingRule rule = new ObjectMapper().readValue("[\"orders\",6]", RoutingRule.class);

        assertThat(rule.topic).isEqualTo("orders");
        assertThat(rule.partitions).isEqualTo(6);
    }

    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({"topic", "partitions"})
    @JsonDeserialize(builder = RoutingRule.Builder.class)
    public static final class RoutingRule {
        private final String topic;
        private final int partitions;

        private RoutingRule(String topic, int partitions) {
            this.topic = topic;
            this.partitions = partitions;
        }

        @JsonPOJOBuilder(withPrefix = "")
        public static final class Builder {
            private String topic;
            private int partitions;

            public Builder topic(String topic) {
                this.topic = topic;
                return this;
            }

            public Builder partitions(int partitions) {
                this.partitions = partitions;
                return this;
            }

            public RoutingRule build() {
                return new RoutingRule(topic, partitions);
            }
        }
    }
}
