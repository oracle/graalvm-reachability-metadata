/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.Test;

public class EqualsBuilderTest {
    @Test
    void comparesPrivateStateReflectively() {
        Value first = new Value("pulsar", 4);

        assertThat(EqualsBuilder.reflectionEquals(first, new Value("pulsar", 4))).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(first, new Value("pulsar", 5))).isFalse();
    }

    public static class Value {
        private final String name;
        private final int count;

        public Value(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
