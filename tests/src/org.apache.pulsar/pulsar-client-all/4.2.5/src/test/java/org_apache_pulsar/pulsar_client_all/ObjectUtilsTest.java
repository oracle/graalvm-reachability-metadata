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
    void clonesPrimitiveArraysAndPubliclyCloneableObjects() {
        int[] numbers = {1, 2, 3};
        CloneableValue value = new CloneableValue("pulsar");

        int[] clonedNumbers = ObjectUtils.clone(numbers);
        CloneableValue clonedValue = ObjectUtils.clone(value);

        assertThat(clonedNumbers).containsExactly(1, 2, 3).isNotSameAs(numbers);
        assertThat(clonedValue.text).isEqualTo("pulsar");
        assertThat(clonedValue).isNotSameAs(value);
    }

    public static class CloneableValue implements Cloneable {
        private final String text;

        public CloneableValue(String text) {
            this.text = text;
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
