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
        final int[] source = {2, 4, 6};
        final CloneableValue value = new CloneableValue("orders");

        final int[] copiedArray = ObjectUtils.clone(source);
        final CloneableValue copiedValue = ObjectUtils.clone(value);

        assertThat(copiedArray).containsExactly(2, 4, 6).isNotSameAs(source);
        assertThat(copiedValue).isNotSameAs(value);
        assertThat(copiedValue.name).isEqualTo("orders");
    }

    public static final class CloneableValue implements Cloneable {
        private final String name;

        public CloneableValue(String name) {
            this.name = name;
        }

        @Override
        public CloneableValue clone() {
            try {
                return (CloneableValue) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }
}
