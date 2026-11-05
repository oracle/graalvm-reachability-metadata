/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.jboss.weld.util.collections.Arrays2;
import org.junit.jupiter.api.Test;

public class Arrays2Test {
    @Test
    void copiesTypedArrayWithPaddingAndTruncation() {
        String[] original = {"alpha", "beta"};
        String[] padded = Arrays2.copyOf(original, 4);
        assertThat(padded).isInstanceOf(String[].class).containsExactly("alpha", "beta", null, null);
        padded[0] = "changed";
        assertThat(original).containsExactly("alpha", "beta");
        assertThat(Arrays2.copyOf(original, 1)).containsExactly("alpha");
        Number[] widened = Arrays2.copyOf(new Integer[] {3, 7}, 3, Number[].class);
        assertThat(widened).isInstanceOf(Number[].class).containsExactly(3, 7, null);
    }
}
