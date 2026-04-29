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

public class PlatformTest {
    @Test
    void newArrayFromReferenceKeepsRuntimeComponentType() {
        CharSequence[] reference = new StringBuilder[] {new StringBuilder("seed")};

        CharSequence[] copy = ObjectArrays.newArray(reference, 3);

        assertThat(copy).isInstanceOf(StringBuilder[].class);
        assertThat(copy).hasSize(3);
        assertThat(copy).containsOnlyNulls();
    }
}
