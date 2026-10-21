/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.HashCodeBuilder;
import org.junit.jupiter.api.Test;

public class HashCodeBuilderTest {
    @Test
    void hashesPrivateStateReflectively() {
        int first = HashCodeBuilder.reflectionHashCode(new Value("pulsar", 4));
        int matching = HashCodeBuilder.reflectionHashCode(new Value("pulsar", 4));
        int different = HashCodeBuilder.reflectionHashCode(new Value("pulsar", 5));

        assertThat(first).isEqualTo(matching).isNotEqualTo(different);
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
