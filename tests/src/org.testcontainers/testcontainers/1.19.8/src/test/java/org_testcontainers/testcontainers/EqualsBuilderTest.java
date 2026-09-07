/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.builder.EqualsBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class EqualsBuilderTest {
    @Test
    void comparesObjectFieldsReflectively() {
        assertThat(EqualsBuilder.reflectionEquals(new Value(3, "same"), new Value(3, "same"))).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(new Value(3, "same"), new Value(4, "same"))).isFalse();
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
