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
    void comparesPrivateStateReflectively() {
        RankedValue lower = new RankedValue("alpha", 1);
        RankedValue higher = new RankedValue("alpha", 2);

        assertThat(CompareToBuilder.reflectionCompare(lower, higher)).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(higher, lower)).isPositive();
        assertThat(CompareToBuilder.reflectionCompare(lower, new RankedValue("alpha", 1))).isZero();
    }

    public static class RankedValue {
        private final String name;
        private final int rank;

        public RankedValue(String name, int rank) {
            this.name = name;
            this.rank = rank;
        }
    }
}
