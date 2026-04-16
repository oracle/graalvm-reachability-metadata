/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.collect;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObjectArraysTest {
    @Test
    void createsTypedArrayFromComponentClass() {
        final String[] values = $ObjectArrays.newArray(String.class, 3);

        assertThat(values).hasSize(3).containsOnlyNulls();
        assertThat(values).isInstanceOf(String[].class);
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
    }
}
