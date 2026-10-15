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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ObjectUtilsTest {
    @Test
    void clonesPrimitiveArraysAndCloneableObjects() {
        int[] numbers = {1, 2, 3};
        CloneableValue value = new CloneableValue("cursor");

        int[] copiedNumbers = ObjectUtils.clone(numbers);
        CloneableValue copiedValue = ObjectUtils.clone(value);

        assertThat(copiedNumbers).containsExactly(1, 2, 3).isNotSameAs(numbers);
        assertThat(copiedValue).isNotSameAs(value);
        assertThat(copiedValue.name).isEqualTo("cursor");
    }

    public static final class CloneableValue implements Cloneable {
        private final String name;

        CloneableValue(String name) {
            this.name = name;
        }

        @Override
        public CloneableValue clone() {
            return new CloneableValue(name);
        }
    }
}
