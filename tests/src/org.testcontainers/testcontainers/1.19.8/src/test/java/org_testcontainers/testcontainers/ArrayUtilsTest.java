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
    void growsTypedArraysAndInsertsElements() {
        assertThat(ArrayUtils.add((String[]) null, 0, "first")).containsExactly("first");
        assertThat(ArrayUtils.add(new String[]{"first", "last"}, 1, "middle"))
                .containsExactly("first", "middle", "last");
        assertThat(ArrayUtils.add((String[]) null, "first")).containsExactly("first");
        assertThat(ArrayUtils.add(new String[]{"first"}, "last")).containsExactly("first", "last");
        assertThat(ArrayUtils.addAll(new String[]{"first"}, "middle", "last"))
                .containsExactly("first", "middle", "last");
        assertThat(ArrayUtils.insert(1, new String[]{"first", "last"}, "middle"))
                .containsExactly("first", "middle", "last");
        assertThat(ArrayUtils.nullToEmpty(null, String[].class)).isInstanceOf(String[].class).isEmpty();
    }

    @Test
    void removesElementsAndSlicesWithoutChangingTheSource() {
        String[] values = {"first", "middle", "last", "middle"};
        assertThat(ArrayUtils.remove(values, 1)).containsExactly("first", "last", "middle");
        assertThat(ArrayUtils.removeAll(values, 1, 3)).containsExactly("first", "last");
        assertThat(ArrayUtils.removeElements(values, "middle", "middle")).containsExactly("first", "last");
        assertThat(ArrayUtils.subarray(values, 1, 3)).containsExactly("middle", "last");
        assertThat(ArrayUtils.subarray(values, 3, 1)).isInstanceOf(String[].class).isEmpty();
        assertThat(values).containsExactly("first", "middle", "last", "middle");
    }
}
