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
    public void createsTypedArrayFromComponentType() {
        String[] names = ArrayUtil.newArray(String.class, 3);

        assertThat(names).hasSize(3).containsOnlyNulls();
        assertThat(names.getClass()).isEqualTo(String[].class);
    }

    @Test
    public void resolvesArrayClassFromComponentType() {
        Class<?> arrayType = ArrayUtil.getArrayType(Integer.class);

        assertThat(arrayType).isEqualTo(Integer[].class);
    }

    @Test
    public void resizesPrimitiveArray() {
        int[] numbers = {1, 2, 3};

        Object resized = ArrayUtil.resize(numbers, 5);

        assertThat(resized).isInstanceOf(int[].class);
        assertThat((int[]) resized).containsExactly(1, 2, 3, 0, 0);
    }

    @Test
    public void clonesPrimitiveArray() {
        int[] numbers = {4, 5, 6};

        int[] cloned = ArrayUtil.clone(numbers);

        assertThat(cloned).isNotSameAs(numbers).containsExactly(4, 5, 6);
    }
}
