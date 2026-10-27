/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.apache.pulsar.shade.org.apache.commons.lang3.reflect.ConstructorUtils;
import org.junit.jupiter.api.Test;

public class ConstructorUtilsTest {
    @Test
    void resolvesExactAndAssignablePublicConstructors() throws Exception {
        Constructor<Constructed> exact = ConstructorUtils.getAccessibleConstructor(Constructed.class, String.class);
        Constructor<Constructed> matching = ConstructorUtils.getMatchingAccessibleConstructor(
                Constructed.class, Integer.class);
        Constructed exactValue = ConstructorUtils.invokeExactConstructor(Constructed.class, "pulsar");
        Constructed compatibleValue = ConstructorUtils.invokeConstructor(Constructed.class, Integer.valueOf(7));

        assertThat(exact).isNotNull();
        assertThat(matching.getParameterTypes()).containsExactly(Number.class);
        assertThat(exactValue.description()).isEqualTo("text:pulsar");
        assertThat(compatibleValue.description()).isEqualTo("number:7");
    }

    public static class Constructed {
        private final String description;

        public Constructed(String value) {
            description = "text:" + value;
        }

        public Constructed(Number value) {
            description = "number:" + value;
        }

        public String description() {
            return description;
        }
    }
}
