/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.pulsar.shade.org.apache.commons.lang3.builder.ToStringStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ReflectionToStringBuilderTest {
    @Test
    void formatsPrivateAndInheritedFields() {
        BrokerState state = new BrokerState("broker-a", 7);

        String text = ReflectionToStringBuilder.toString(
                state, ToStringStyle.SHORT_PREFIX_STYLE);

        assertThat(text).contains("broker-a", "connections=7", "region=local");
    }

    public static class RegionalState {
        private final String region = "local";
    }

    public static final class BrokerState extends RegionalState {
        private final String name;
        private final int connections;

        public BrokerState(String name, int connections) {
            this.name = name;
            this.connections = connections;
        }
    }
}
