/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import jersey.repackaged.com.google.common.collect.ObjectArrays;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectArraysTest {
    @Test
    void newArrayCreatesArrayWithRequestedComponentTypeAndLength() {
        CharSequence[] values = ObjectArrays.newArray(CharSequence.class, 3);

        assertThat(values).isInstanceOf(CharSequence[].class);
        assertThat(values).hasSize(3);
        assertThat(values).containsOnlyNulls();
    }
}
