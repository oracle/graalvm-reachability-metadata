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
    void removesElementFromPrimitiveArrayByIndex() {
        int[] values = {1, 2, 3, 4};

        int[] result = PrimitiveArrayUtil.remove(values, 1);

        assertThat(result).isNotSameAs(values);
        assertThat(result).containsExactly(1, 3, 4);
    }
}
