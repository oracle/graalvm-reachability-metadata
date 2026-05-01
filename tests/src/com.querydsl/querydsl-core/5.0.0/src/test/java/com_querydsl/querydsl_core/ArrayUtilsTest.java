/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.util.ArrayUtils;

import org.junit.jupiter.api.Test;

public class ArrayUtilsTest {

    @Test
    void combinesFirstTwoValuesWithTypedRestArray() {
        String[] result = ArrayUtils.combine(String.class, "alpha", "beta", new String[] {"gamma", "delta"});

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma", "delta");
    }

    @Test
    void combinesFirstValueWithTypedRestArray() {
        String[] result = ArrayUtils.combine(String.class, "alpha", new String[] {"beta", "gamma"});

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void subarrayPreservesComponentTypeWhenRangeContainsValues() {
        Object[] result = ArrayUtils.subarray(new Integer[] {1, 2, 3, 4}, 1, 3);

        assertThat(result)
                .isInstanceOf(Integer[].class)
                .containsExactly(2, 3);
    }

    @Test
    void subarrayPreservesComponentTypeForEmptyRange() {
        Object[] result = ArrayUtils.subarray(new String[] {"alpha", "beta"}, 2, 2);

        assertThat(result)
                .isInstanceOf(String[].class)
                .isEmpty();
    }
}
