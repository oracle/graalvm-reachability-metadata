/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml.aalto_xml;

import com.fasterxml.aalto.util.DataUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

public class DataUtilTest {
    @Test
    void growAnyArrayByPreservesObjectArrayElements() {
        String[] original = new String[] {"root", "child"};

        Object grown = DataUtil.growAnyArrayBy(original, 2);

        assertThat(grown).isInstanceOf(String[].class);
        assertThat((String[]) grown).containsExactly("root", "child", null, null);
        assertThat(grown).isNotSameAs(original);
    }

    @Test
    void growAnyArrayByPreservesPrimitiveArrayElements() {
        int[] original = new int[] {3, 5, 8};

        Object grown = DataUtil.growAnyArrayBy(original, 1);

        assertThat(grown).isInstanceOf(int[].class);
        assertThat((int[]) grown).containsExactly(3, 5, 8, 0);
        assertThat(grown).isNotSameAs(original);
    }

    @Test
    void growAnyArrayByRejectsNullArray() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> DataUtil.growAnyArrayBy(null, 1))
                .withMessage("Null array");
    }
}
