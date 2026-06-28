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
    void clonesPrimitiveArraysUsingObjectUtilsClone() {
        int[] original = new int[] { 1, 2, 3 };

        int[] cloned = ObjectUtils.clone(original);

        assertThat(cloned).containsExactly(1, 2, 3);
        assertThat(cloned).isNotSameAs(original);
    }

    @Test
    void clonesCloneableObjectsUsingTheirPublicCloneMethod() {
        CopyableCounter original = new CopyableCounter(42);

        CopyableCounter cloned = ObjectUtils.clone(original);

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.value()).isEqualTo(42);
    }

    public static final class CopyableCounter implements Cloneable {
        private final int value;

        CopyableCounter(int value) {
            this.value = value;
        }

        public int value() {
            return value;
        }

        @Override
        public CopyableCounter clone() {
            return new CopyableCounter(value);
        }
    }
}
