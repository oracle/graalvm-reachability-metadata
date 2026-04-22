/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.Arrays;

import org.eclipse.jetty.util.LazyList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyListCoverageTest {
    @Test
    void lazyListConvertsMultipleShapesToArrays() {
        assertThat((String[]) LazyList.toArray(null, String.class)).isEmpty();
        assertThat((int[]) LazyList.toArray(Arrays.asList(1, 2), Integer.TYPE)).containsExactly(1, 2);
        assertThat((String[]) LazyList.toArray(Arrays.asList("a", "b"), String.class)).containsExactly("a", "b");
        assertThat((String[]) LazyList.toArray("single", String.class)).containsExactly("single");
    }
}
