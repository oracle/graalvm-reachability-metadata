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
    void combineWithFirstAndSecondCreatesTypedArray() {
        String[] combined = ArrayUtils.combine(String.class, "alpha", "beta", new String[] {"gamma", "delta"});

        assertThat(combined).isInstanceOf(String[].class);
        assertThat(combined).containsExactly("alpha", "beta", "gamma", "delta");
    }

    @Test
    void combineWithSingleFirstElementCreatesTypedArray() {
        String[] combined = ArrayUtils.combine(String.class, "alpha", new String[] {"beta", "gamma"});

        assertThat(combined).isInstanceOf(String[].class);
        assertThat(combined).containsExactly("alpha", "beta", "gamma");
    }

    @Test
    void subarrayWithEmptyRangeKeepsComponentType() {
        String[] input = {"alpha", "beta", "gamma"};

        Object[] subarray = ArrayUtils.subarray(input, 2, 2);

        assertThat(subarray).isInstanceOf(String[].class);
        assertThat(subarray).isEmpty();
    }

    @Test
    void subarrayWithPositiveRangeKeepsComponentTypeAndCopiesValues() {
        String[] input = {"alpha", "beta", "gamma", "delta"};

        Object[] subarray = ArrayUtils.subarray(input, 1, 3);

        assertThat(subarray).isInstanceOf(String[].class);
        assertThat(subarray).containsExactly("beta", "gamma");
    }
}
