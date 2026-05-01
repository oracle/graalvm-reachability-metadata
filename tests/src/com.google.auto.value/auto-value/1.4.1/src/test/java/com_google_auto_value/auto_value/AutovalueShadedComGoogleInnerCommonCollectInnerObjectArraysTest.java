/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.collect.$ObjectArrays;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerObjectArraysTest {
    @Test
    void newArrayCreatesTypedArrayWithRequestedLength() {
        String[] values = $ObjectArrays.newArray(String.class, 3);

        assertThat(values).hasSize(3);
        assertThat(values.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(values).containsExactly(null, null, null);
    }

    @Test
    void concatCreatesTypedArrayAndPreservesElementOrder() {
        String[] prefix = {"alpha", "beta"};
        String[] suffix = {"gamma"};

        String[] combined = $ObjectArrays.concat(prefix, suffix, String.class);

        assertThat(combined).isInstanceOf(String[].class);
        assertThat(combined.getClass().getComponentType()).isEqualTo(String.class);
        assertThat(combined).containsExactly("alpha", "beta", "gamma");
    }
}
