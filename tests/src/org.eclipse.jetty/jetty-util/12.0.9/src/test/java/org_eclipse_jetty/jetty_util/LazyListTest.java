/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.util.List;

import org.eclipse.jetty.util.LazyList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyListTest {
    @Test
    void convertsEveryLazyListShapeToTypedArrays() {
        String[] empty = (String[]) LazyList.toArray(null, String.class);
        assertThat(empty).isEmpty();

        int[] primitiveValues = (int[]) LazyList.toArray(List.of(3, 5), Integer.TYPE);
        assertThat(primitiveValues).containsExactly(3, 5);

        String[] listedValues = (String[]) LazyList.toArray(List.of("one", "two"), String.class);
        assertThat(listedValues).containsExactly("one", "two");

        String[] singleValue = (String[]) LazyList.toArray("only", String.class);
        assertThat(singleValue).containsExactly("only");
    }
}
