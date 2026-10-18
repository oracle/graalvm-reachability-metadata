/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.builder.CompareToBuilder;
import org.junit.jupiter.api.Test;

public class CompareToBuilderTest {

    @Test
    void comparesDomainValuesByTheirFields() {
        final int comparison = CompareToBuilder.reflectionCompare(new Sequence(7, "a"), new Sequence(9, "a"));

        assertThat(comparison).isNegative();
    }

    public static final class Sequence {
        private final int value;
        private final String partition;

        public Sequence(int value, String partition) {
            this.value = value;
            this.partition = partition;
        }
    }
}
