/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.util.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {
    @Test
    void combineWithTwoLeadingValuesPreservesComponentTypeAndOrder() {
        String[] combined = ArrayUtils.combine(String.class, "alpha", "bravo", new String[] {"charlie", "delta"});

        assertThat(combined)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "bravo", "charlie", "delta");
    }

    @Test
    void combineWithOneLeadingValuePreservesComponentTypeAndOrder() {
        String[] combined = ArrayUtils.combine(String.class, "alpha", new String[] {"bravo", "charlie"});

        assertThat(combined)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void subarrayWithPositiveSizePreservesComponentTypeAndCopiesRequestedRange() {
        Object[] subarray = ArrayUtils.subarray(new String[] {"alpha", "bravo", "charlie", "delta"}, 1, 3);

        assertThat(subarray)
                .isInstanceOf(String[].class)
                .containsExactly("bravo", "charlie");
    }

    @Test
    void subarrayWithNonPositiveSizeReturnsEmptyArrayWithOriginalComponentType() {
        Object[] subarray = ArrayUtils.subarray(new String[] {"alpha", "bravo"}, 1, 1);

        assertThat(subarray)
                .isInstanceOf(String[].class)
                .isEmpty();
    }
}
