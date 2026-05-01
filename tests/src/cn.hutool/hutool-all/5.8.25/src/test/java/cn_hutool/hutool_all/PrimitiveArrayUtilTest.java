/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.PrimitiveArrayUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PrimitiveArrayUtilTest {
    @Test
    public void removesElementFromPrimitiveArrayByIndex() {
        int[] numbers = {3, 5, 8, 13};

        int[] result = PrimitiveArrayUtil.remove(numbers, 2);

        assertThat(result).isNotSameAs(numbers).containsExactly(3, 5, 13);
        assertThat(result.getClass()).isEqualTo(int[].class);
    }

    @Test
    public void returnsOriginalPrimitiveArrayForInvalidIndex() {
        boolean[] flags = {true, false};

        boolean[] result = PrimitiveArrayUtil.remove(flags, -1);

        assertThat(result).isSameAs(flags).containsExactly(true, false);
    }
}
