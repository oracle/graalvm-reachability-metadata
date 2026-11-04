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
    void comparesPrivateFieldsWithOptionalFieldExclusions() {
        Destination source = new Destination("queue", 2);
        assertThat(EqualsBuilder.reflectionEquals(source, new Destination("queue", 2))).isTrue();
        assertThat(EqualsBuilder.reflectionEquals(source, new Destination("worker", 2))).isFalse();
        assertThat(EqualsBuilder.reflectionEquals(source, new Destination("queue", 3))).isFalse();
        assertThat(EqualsBuilder.reflectionEquals(source, new Destination("queue", 3), "attempts")).isTrue();
    }

    public static class Destination {
        private final String name;
        private final int attempts;

        public Destination(String name, int attempts) {
            this.name = name;
            this.attempts = attempts;
        }
    }
}
