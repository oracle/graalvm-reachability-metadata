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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ConstructorUtilsTest {
    @Test
    void selectsExactAndAssignmentCompatibleConstructors() throws Exception {
        Constructed exact =
                ConstructorUtils.invokeExactConstructor(Constructed.class, "exact");
        Constructed compatible =
                ConstructorUtils.invokeConstructor(Constructed.class, Integer.valueOf(7));

        assertThat(exact.value()).isEqualTo("exact");
        assertThat(compatible.value()).isEqualTo("number:7");
        assertThat(ConstructorUtils.getAccessibleConstructor(Constructed.class, String.class))
                .isNotNull();
        assertThat(ConstructorUtils.getMatchingAccessibleConstructor(
                        Constructed.class, Integer.class))
                .isNotNull();
    }

    public static final class Constructed {
        private final String value;

        public Constructed(String value) {
            this.value = value;
        }

        public Constructed(Number value) {
            this.value = "number:" + value;
        }

        public String value() {
            return value;
        }
    }
}
