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
    void createsTypedArrayAndReportsArrayClass() {
        String[] names = ArrayUtil.newArray(String.class, 2);

        assertThat(names).containsExactly(null, null);
        assertThat(names).isInstanceOf(String[].class);
        assertThat(ArrayUtil.getArrayType(Integer.class)).isEqualTo(Integer[].class);
    }

    @Test
    void insertsElementsIntoReferenceArray() {
        String[] source = {"alpha", "delta"};

        String[] inserted = ArrayUtil.insert(source, 1, "beta", "gamma");

        assertThat(inserted).containsExactly("alpha", "beta", "gamma", "delta");
        assertThat(inserted).isNotSameAs(source);
        assertThat(source).containsExactly("alpha", "delta");
    }

    @Test
    void resizesArrayThroughObjectApi() {
        String[] source = {"alpha", "beta", "gamma"};

        Object resized = ArrayUtil.resize((Object) source, 5);

        assertThat(resized).isInstanceOf(String[].class);
        assertThat((String[]) resized).containsExactly("alpha", "beta", "gamma", null, null);
    }

    @Test
    void clonesPrimitiveArrayThroughObjectApi() {
        int[] source = {1, 2, 3};

        int[] cloned = ArrayUtil.clone(source);

        assertThat(cloned).containsExactly(1, 2, 3);
        assertThat(cloned).isNotSameAs(source);
        source[0] = 99;
        assertThat(cloned).containsExactly(1, 2, 3);
    }
}
