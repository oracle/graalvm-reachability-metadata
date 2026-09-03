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

public class ArrayUtilTest {
    @Test
    void createsArraysWhileAddingPrependingAndRemovingValues() {
        String[] added = ArrayUtil.addToArray(null, "second", String.class);
        assertThat(added).containsExactly("second");

        String[] prependedToNull = ArrayUtil.prependToArray("first", null, String.class);
        assertThat(prependedToNull).containsExactly("first");

        String[] prepended = ArrayUtil.prependToArray("first", added, String.class);
        assertThat(prepended).containsExactly("first", "second");

        String[] removed = ArrayUtil.removeFromArray(prepended, "first");
        assertThat(removed).containsExactly("second");
    }
}
