/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.HashCodeBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class HashCodeBuilderTest {
    @Test
    void hashesObjectFieldsReflectively() {
        int first = HashCodeBuilder.reflectionHashCode(new Value(3, "same"));
        int second = HashCodeBuilder.reflectionHashCode(new Value(3, "same"));
        int different = HashCodeBuilder.reflectionHashCode(new Value(4, "same"));

        assertThat(first).isEqualTo(second).isNotEqualTo(different);
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
