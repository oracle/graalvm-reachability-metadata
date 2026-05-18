/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_framework;

import org.glassfish.grizzly.utils.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {
    @Test
    void removeCreatesTypedArrayWithoutRemovedElement() {
        String[] values = {"alpha", "beta", "gamma"};

        String[] result = ArrayUtils.remove(values, "beta");

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "gamma");
    }
}
