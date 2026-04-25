/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import org.immutables.value.internal.$guava$.collect.$ObjectArrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectArraysTest {

    @Test
    void newArrayCreatesTypedArrayOfRequestedLength() {
        String[] values = $ObjectArrays.newArray(String.class, 3);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).hasSize(3);
        assertThat(values).containsOnlyNulls();
    }
}
