/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.apache.pulsar.shade.com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class BuilderBasedDeserializerTest {
    @Test
    void finishesImmutableValueWithConfiguredBuilder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        Subscription restored = mapper.readValue(
                "{\"name\":\"orders-sub\",\"priority\":6}", Subscription.class);

        assertThat(restored.name).isEqualTo("orders-sub");
        assertThat(restored.priority).isEqualTo(6);
    }

    @JsonDeserialize(builder = Subscription.Builder.class)
    public static final class Subscription {
        private final String name;
        private final int priority;

        private Subscription(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        @JsonPOJOBuilder(withPrefix = "with")
        public static final class Builder {
            private String name;
            private int priority;

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withPriority(int priority) {
                this.priority = priority;
                return this;
            }

            public Subscription build() {
                return new Subscription(name, priority);
            }
        }
    }
}
