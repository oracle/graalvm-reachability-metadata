/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_lang3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {

    @Test
    public void cloneCreatesIndependentPrimitiveArrayCopy() {
        int[] original = new int[]{1, 2, 3};

        int[] clone = ObjectUtils.clone(original);
        clone[1] = 99;

        assertThat(clone).isNotSameAs(original).containsExactly(1, 99, 3);
        assertThat(original).containsExactly(1, 2, 3);
    }

    @Test
    public void cloneInvokesPublicCloneMethodOnCloneableObject() {
        CloneableSample original = new CloneableSample("alpha", 7);

        CloneableSample clone = ObjectUtils.clone(original);
        clone.setName("beta");

        assertThat(clone).isNotSameAs(original);
        assertThat(clone.getName()).isEqualTo("beta");
        assertThat(clone.getValue()).isEqualTo(7);
        assertThat(original.getName()).isEqualTo("alpha");
        assertThat(original.getValue()).isEqualTo(7);
    }

    public static final class CloneableSample implements Cloneable {
        private String name;
        private final int value;

        public CloneableSample(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public CloneableSample clone() {
            return new CloneableSample(name, value);
        }
    }
}
