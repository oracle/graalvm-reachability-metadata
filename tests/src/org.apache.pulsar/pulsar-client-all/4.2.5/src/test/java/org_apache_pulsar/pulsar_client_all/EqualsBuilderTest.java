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
    void comparesFieldsReflectively() {
        assertThat(EqualsBuilder.reflectionEquals(new Value("topic", 3), new Value("topic", 3))).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(new Value("topic", 3), new Value("topic", 4))).isFalse();
    }

    public static final class Value {
        private final String name;
        private final int count;

        public Value(String name, int count) {
            this.name = name;
            this.count = count;
        }
    }
}
