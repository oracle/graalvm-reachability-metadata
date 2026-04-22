/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import org.eclipse.jetty.util.ArrayUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilCoverageTest {
    @Test
    void arrayUtilCreatesArraysThroughReflectionHelpers() {
        assertThat(ArrayUtil.removeFromArray(new String[]{"a", "b"}, "a")).containsExactly("b");
        assertThat(ArrayUtil.addToArray(null, "a", String.class)).containsExactly("a");
        assertThat(ArrayUtil.prependToArray("a", null, String.class)).containsExactly("a");
        assertThat(ArrayUtil.prependToArray("z", new String[]{"a", "b"}, String.class)).containsExactly("z", "a", "b");
    }
}
