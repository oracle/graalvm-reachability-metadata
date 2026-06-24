/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ObjectArrays;

import org.junit.jupiter.api.Test;

public class PlatformTest {
    @Test
    void objectArraysCreatesArrayFromComponentType() {
        String[] values = ObjectArrays.newArray(String.class, 3);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).containsExactly(null, null, null);
    }

    @Test
    void objectArraysCreatesArrayFromReferenceArrayType() {
        Integer[] reference = new Integer[] {1, 2};

        Integer[] values = ObjectArrays.newArray(reference, 4);

        assertThat(values).isInstanceOf(Integer[].class);
        assertThat(values).containsExactly(null, null, null, null);
    }
}
