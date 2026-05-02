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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DataUtilTest {
    @Test
    void growsReferenceArrayPreservingComponentTypeAndContents() {
        String[] originalValues = {"alpha", "beta"};

        Object grownArray = DataUtil.growAnyArrayBy(originalValues, 2);

        assertThat(grownArray).isInstanceOf(String[].class);
        assertThat((String[]) grownArray).containsExactly("alpha", "beta", null, null);
        assertThat(grownArray).isNotSameAs(originalValues);
    }

    @Test
    void growsPrimitiveArrayPreservingComponentTypeAndContents() {
        int[] originalValues = {3, 5, 8};

        Object grownArray = DataUtil.growAnyArrayBy(originalValues, 1);

        assertThat(grownArray).isInstanceOf(int[].class);
        assertThat((int[]) grownArray).containsExactly(3, 5, 8, 0);
        assertThat(grownArray).isNotSameAs(originalValues);
    }

    @Test
    void rejectsNullInputForGenericArrayGrowth() {
        assertThatThrownBy(() -> DataUtil.growAnyArrayBy(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Null array");
    }
}
