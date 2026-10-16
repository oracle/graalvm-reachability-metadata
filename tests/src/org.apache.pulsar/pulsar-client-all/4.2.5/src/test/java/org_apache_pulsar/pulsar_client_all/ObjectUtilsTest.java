/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.ObjectUtils;
import org.junit.jupiter.api.Test;

public class ObjectUtilsTest {
    @Test
    void clonesPrimitiveArraysAndCloneableObjects() {
        final int[] source = {1, 2, 3};
        final int[] clonedArray = ObjectUtils.clone(source);
        final CloneableValue clonedValue = ObjectUtils.clone(new CloneableValue("pulsar"));

        assertThat(clonedArray).containsExactly(1, 2, 3).isNotSameAs(source);
        assertThat(clonedValue.value).isEqualTo("pulsar");
    }

    public static final class CloneableValue implements Cloneable {
        private final String value;

        public CloneableValue(String value) {
            this.value = value;
        }

        @Override
        public CloneableValue clone() {
            try {
                return (CloneableValue) super.clone();
            } catch (CloneNotSupportedException exception) {
                throw new AssertionError(exception);
            }
        }
    }
}
