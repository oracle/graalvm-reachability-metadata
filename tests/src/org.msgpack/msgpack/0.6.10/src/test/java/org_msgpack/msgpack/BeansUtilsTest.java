/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_msgpack.msgpack;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.harmony.beans.BeansUtils;
import org.junit.jupiter.api.Test;

public class BeansUtilsTest {
    @Test
    void detectsClassThatDeclaresEqualsMethod() {
        assertThat(BeansUtils.declaredEquals(BeanWithEquals.class)).isTrue();
    }

    @Test
    void ignoresInheritedEqualsMethod() {
        assertThat(BeansUtils.declaredEquals(BeanWithoutEquals.class)).isFalse();
    }

    public static final class BeanWithEquals {
        private final String value;

        public BeanWithEquals(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof BeanWithEquals)) {
                return false;
            }
            BeanWithEquals that = (BeanWithEquals) other;
            return this.value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return this.value.hashCode();
        }
    }

    public static final class BeanWithoutEquals {
        private final String value;

        public BeanWithoutEquals(String value) {
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }
    }
}
