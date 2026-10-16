/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.ConstructorUtils;
import org.junit.jupiter.api.Test;

public class ConstructorUtilsTest {
    @Test
    void findsAndInvokesExactAndAssignableConstructors() throws Exception {
        assertThat(ConstructorUtils.getAccessibleConstructor(ConstructorFixture.class, String.class)).isNotNull();
        assertThat(ConstructorUtils.getMatchingAccessibleConstructor(ConstructorFixture.class, Integer.class))
                .isNotNull();

        final ConstructorFixture exact = ConstructorUtils.invokeExactConstructor(ConstructorFixture.class, "exact");
        final ConstructorFixture matching = ConstructorUtils.invokeConstructor(
                ConstructorFixture.class, Integer.valueOf(42));

        assertThat(exact.value).isEqualTo("exact");
        assertThat(matching.value).isEqualTo("42");
    }

    public static final class ConstructorFixture {
        private final String value;

        public ConstructorFixture(String value) {
            this.value = value;
        }

        public ConstructorFixture(Number value) {
            this.value = value.toString();
        }
    }
}
