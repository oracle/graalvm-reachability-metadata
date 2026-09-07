/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.ObjectUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectUtilsTest {
    @Test
    void clonesArraysAndPubliclyCloneableObjects() {
        String[] array = {"one", "two"};
        int[] primitiveArray = {1, 2};
        CloneableValue value = new CloneableValue("copied");

        assertThat(ObjectUtils.clone(array)).containsExactly("one", "two").isNotSameAs(array);
        assertThat(ObjectUtils.clone(primitiveArray)).containsExactly(1, 2).isNotSameAs(primitiveArray);
        assertThat(ObjectUtils.clone(value)).isEqualTo(value).isNotSameAs(value);
    }

    public static class CloneableValue implements Cloneable {
        private final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        @Override
        public CloneableValue clone() {
            return new CloneableValue(value);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CloneableValue && value.equals(((CloneableValue) other).value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
