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
    void selectsAndInvokesPublicConstructors() throws Exception {
        Constructed matching = ConstructorUtils.invokeConstructor(Constructed.class, Integer.valueOf(9));
        Constructed exact = ConstructorUtils.invokeExactConstructor(Constructed.class, Integer.valueOf(8));
        Constructed compatible = ConstructorUtils.invokeConstructor(Constructed.class, Long.valueOf(7));

        assertThat(matching.value).isEqualTo(9);
        assertThat(exact.value).isEqualTo(8);
        assertThat(compatible.value).isEqualTo(7);
        assertThat(ConstructorUtils.getAccessibleConstructor(Constructed.class, Integer.class)).isNotNull();
        assertThat(ConstructorUtils.getMatchingAccessibleConstructor(Constructed.class, Integer.class)).isNotNull();
    }

    public static class Constructed {
        private final int value;

        public Constructed(Number value) {
            this.value = value.intValue();
        }

        public Constructed(Integer value) {
            this.value = value;
        }
    }
}
