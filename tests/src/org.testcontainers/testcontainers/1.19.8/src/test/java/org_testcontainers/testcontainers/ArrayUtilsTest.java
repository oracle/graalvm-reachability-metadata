/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.apache.commons.lang3.ArrayUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {
    @Test
    void createsTypedArraysForArrayTransformations() {
        String[] values = {"b", "c", "c", "d"};

        assertThat(ArrayUtils.add(values, "e")).containsExactly("b", "c", "c", "d", "e");
        assertThat(ArrayUtils.add(values, 0, "a")).containsExactly("a", "b", "c", "c", "d");
        assertThat(ArrayUtils.add((String[]) null, "a")).containsExactly("a");
        assertThat(ArrayUtils.add((String[]) null, 0, "a")).containsExactly("a");
        assertThat(ArrayUtils.addAll(values, "e", "f")).containsExactly("b", "c", "c", "d", "e", "f");
        assertThat(ArrayUtils.insert(1, values, "x", "y")).containsExactly("b", "x", "y", "c", "c", "d");
        assertThat(ArrayUtils.nullToEmpty((String[]) null)).isEmpty();
        assertThat(ArrayUtils.nullToEmpty(null, String[].class)).isEmpty();
        assertThat(ArrayUtils.remove(values, 0)).containsExactly("c", "c", "d");
        assertThat(ArrayUtils.removeAll(values, 1, 2)).containsExactly("b", "d");
        assertThat(ArrayUtils.removeElements(values, "c", "d")).containsExactly("b", "c");
        assertThat(ArrayUtils.subarray(values, 1, 3)).containsExactly("c", "c");
        assertThat(ArrayUtils.subarray(values, 9, 12)).isEmpty();
    }
}
