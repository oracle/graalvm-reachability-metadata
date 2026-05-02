/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jersey_bundles_repackaged.jersey_guava;

import static org.assertj.core.api.Assertions.assertThat;

import jersey.repackaged.com.google.common.collect.ObjectArrays;

import org.junit.jupiter.api.Test;

public class PlatformTest {
    @Test
    void newArrayUsesReferenceRuntimeComponentType() {
        final Number[] reference = new Integer[] {1, 2};

        final Number[] values = ObjectArrays.newArray(reference, 3);

        assertThat(values).isInstanceOf(Integer[].class);
        assertThat(values).hasSize(3);
        assertThat(values).containsExactly(null, null, null);
    }
}
