/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.maven_surefire_common;

import org.apache.maven.surefire.shade.org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectUtilsTest {
    @Test
    void clonesPrimitiveArrayUsingItsComponentType() {
        int[] source = {1, 2, 3};

        int[] clone = ObjectUtils.clone(source);

        assertThat(clone).containsExactly(1, 2, 3);
        assertThat(clone).isNotSameAs(source);
    }

    @Test
    void clonesCloneableObjectThroughItsPublicCloneMethod() {
        CloneableMessage source = new CloneableMessage("surefire");

        CloneableMessage clone = ObjectUtils.clone(source);

        assertThat(clone).isEqualTo(source);
        assertThat(clone).isNotSameAs(source);
    }

    public static final class CloneableMessage implements Cloneable {
        private final String value;

        public CloneableMessage(String value) {
            this.value = value;
        }

        @Override
        public CloneableMessage clone() {
            return new CloneableMessage(value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CloneableMessage)) {
                return false;
            }
            CloneableMessage that = (CloneableMessage) other;
            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }
    }
}
