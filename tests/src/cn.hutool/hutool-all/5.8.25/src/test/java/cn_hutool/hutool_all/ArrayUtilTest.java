/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilTest {

    @Test
    void createsTypedArraysFromComponentTypes() {
        String[] strings = ArrayUtil.newArray(String.class, 3);

        assertThat(strings).hasSize(3);
        strings[0] = "alpha";
        assertThat(strings).containsExactly("alpha", null, null);
    }

    @Test
    void resolvesArrayClassFromComponentType() {
        Class<?> arrayType = ArrayUtil.getArrayType(Long.class);

        assertThat(arrayType).isSameAs(Long[].class);
    }

    @Test
    void resizesPrimitiveArrayObjects() {
        Object resized = ArrayUtil.resize(new int[] {1, 2, 3}, 5);

        assertThat(resized).isInstanceOf(int[].class);
        assertThat((int[]) resized).containsExactly(1, 2, 3, 0, 0);
    }

    @Test
    void clonesPrimitiveArrayObjects() {
        int[] values = {4, 5, 6};

        int[] cloned = ArrayUtil.clone(values);

        assertThat(cloned).isNotSameAs(values);
        assertThat(cloned).containsExactly(4, 5, 6);
    }
}
