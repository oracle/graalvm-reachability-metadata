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
    void newArrayWithComponentTypeCreatesArrayOfRequestedRuntimeType() {
        String[] values = ObjectArrays.newArray(String.class, 3);

        assertThat(values).isInstanceOf(String[].class);
        assertThat(values).hasSize(3);
        assertThat(values).containsOnlyNulls();
    }

    @Test
    void newArrayWithReferencePreservesReferenceRuntimeComponentType() {
        Number[] reference = new Integer[] {1, 2};

        Number[] values = ObjectArrays.newArray(reference, 4);

        assertThat(values).isInstanceOf(Integer[].class);
        assertThat(values).hasSize(4);
        assertThat(values).containsOnlyNulls();
    }
}
