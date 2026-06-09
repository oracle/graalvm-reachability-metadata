/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.hadoop.thirdparty.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

public class ObjectArraysTest {
    @Test
    void newArrayCreatesTypedArrayWithRequestedLength() {
        String[] values = ObjectArrays.newArray(String.class, 3);

        assertThat(values).hasSize(3);
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(values).containsExactly(null, null, null);
    }

    @Test
    void concatCreatesTypedArrayForCombinedInputs() {
        Number[] combined = ObjectArrays.concat(
                new Integer[] {1, 2},
                new Double[] {3.5, 4.5},
                Number.class);

        assertThat(combined).containsExactly(1, 2, 3.5, 4.5);
        assertThat(combined.getClass().getComponentType()).isEqualTo(Number.class);
    }
}
