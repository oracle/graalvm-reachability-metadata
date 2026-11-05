/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_core;

import org.glassfish.grizzly.utils.ArrayUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayUtilsTest {
    @Test
    void removesAnElementAndPreservesTheTypedSourceArray() {
        String[] source = {"first", "middle", "last"};

        String[] remaining = ArrayUtils.remove(source, "middle");

        assertThat(remaining).containsExactly("first", "last");
        assertThat(source).containsExactly("first", "middle", "last");
        assertThat(ArrayUtils.remove(remaining, "missing")).isSameAs(remaining);
        assertThat(ArrayUtils.remove(remaining, "first")).containsExactly("last");
        assertThat(ArrayUtils.remove(new String[]{"last"}, "last")).isNull();
    }
}
