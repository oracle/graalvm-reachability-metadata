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
    void hashesPrivateFieldsAndHonorsExcludedFields() {
        Route first = new Route("worker", 4);
        int hash = HashCodeBuilder.reflectionHashCode(first);
        assertThat(hash).isEqualTo(HashCodeBuilder.reflectionHashCode(new Route("worker", 4)));
        assertThat(hash).isNotEqualTo(HashCodeBuilder.reflectionHashCode(new Route("worker", 5)));
        assertThat(HashCodeBuilder.reflectionHashCode(first, "priority"))
                .isEqualTo(HashCodeBuilder.reflectionHashCode(new Route("worker", 5), "priority"));
    }

    public static class Route {
        private final String name;
        private final int priority;

        public Route(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }
    }
}
