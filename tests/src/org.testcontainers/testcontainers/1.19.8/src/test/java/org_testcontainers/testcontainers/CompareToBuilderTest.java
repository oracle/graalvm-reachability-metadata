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
    void comparesObjectFieldsReflectively() {
        assertThat(CompareToBuilder.reflectionCompare(new Value(1, "a"), new Value(2, "a"))).isNegative();
        assertThat(CompareToBuilder.reflectionCompare(new Value(2, "b"), new Value(2, "a"))).isPositive();
    }

    public static class Value {
        private final int number;
        private final String text;

        public Value(int number, String text) {
            this.number = number;
            this.text = text;
        }
    }
}
