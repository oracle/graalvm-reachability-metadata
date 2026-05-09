/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_booter;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {

    @Test
    public void cloneCreatesIndependentPrimitiveArrayCopy() {
        int[] original = new int[] {1, 2, 3};

        int[] clone = ObjectUtils.clone(original);
        clone[1] = 99;

        assertThat(clone).isNotSameAs(original).containsExactly(1, 99, 3);
        assertThat(original).containsExactly(1, 2, 3);
    }

    @Test
    public void cloneInvokesPublicCloneMethodOnCloneableObject() {
        CloneableSample original = new CloneableSample("alpha", new StringBuilder("payload"));

        CloneableSample clone = ObjectUtils.clone(original);
        clone.name("beta");
        clone.payload().append("-copy");

        assertThat(clone).isNotSameAs(original);
        assertThat(clone.name()).isEqualTo("beta");
        assertThat(clone.payload()).hasToString("payload-copy");
        assertThat(original.name()).isEqualTo("alpha");
        assertThat(original.payload()).hasToString("payload");
    }

    public static final class CloneableSample implements Cloneable {
        private String name;
        private final StringBuilder payload;

        public CloneableSample(String name, StringBuilder payload) {
            this.name = name;
            this.payload = payload;
        }

        public String name() {
            return name;
        }

        public void name(String name) {
            this.name = name;
        }

        public StringBuilder payload() {
            return payload;
        }

        @Override
        public CloneableSample clone() {
            return new CloneableSample(name, new StringBuilder(payload));
        }
    }
}
