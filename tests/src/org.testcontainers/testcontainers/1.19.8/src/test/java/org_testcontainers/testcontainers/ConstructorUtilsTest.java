/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.reflect.ConstructorUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ConstructorUtilsTest {
    @Test
    void constructsWithExactAndAssignableParameterTypes() throws Exception {
        Endpoint exact = ConstructorUtils.invokeExactConstructor(Endpoint.class, "service");
        assertThat(exact.description).isEqualTo("service");
        Endpoint assigned = ConstructorUtils.invokeConstructor(Endpoint.class, new StringBuilder("worker"));
        assertThat(assigned.description).isEqualTo("worker");
        assertThat(ConstructorUtils.getAccessibleConstructor(Endpoint.class, String.class))
                .isNotNull().satisfies(constructor -> assertThat(constructor.getParameterTypes())
                        .containsExactly(String.class));
    }

    public static class Endpoint {
        private final String description;

        public Endpoint(String description) {
            this.description = description;
        }

        public Endpoint(CharSequence description) {
            this.description = description.toString();
        }
    }
}
