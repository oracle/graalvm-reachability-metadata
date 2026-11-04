/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.CompareToBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class CompareToBuilderTest {
    @Test
    void ordersObjectsByTheirFieldsAndSupportsExclusions() {
        Priority first = new Priority(1, "alpha");
        Priority second = new Priority(2, "alpha");
        assertThat(CompareToBuilder.reflectionCompare(first, second)).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(second, first)).isPositive();
        assertThat(CompareToBuilder.reflectionCompare(first, new Priority(1, "alpha"))).isZero();
        assertThat(CompareToBuilder.reflectionCompare(first, second, "rank")).isZero();
    }

    public static class Priority {
        private final int rank;
        private final String name;

        public Priority(int rank, String name) {
            this.rank = rank;
            this.name = name;
        }
    }
}
