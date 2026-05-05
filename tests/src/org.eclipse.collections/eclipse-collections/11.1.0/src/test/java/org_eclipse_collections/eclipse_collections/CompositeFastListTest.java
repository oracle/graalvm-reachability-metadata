/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.list.mutable.CompositeFastList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.junit.jupiter.api.Test;

public class CompositeFastListTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final CompositeFastList<String> list = new CompositeFastList<>();
        list.addComposited(FastList.newListWith("alpha", "bravo"));
        list.addComposited(FastList.newListWith("charlie"));

        final Object[] result = list.toArray(new String[0]);

        assertThat(result).isInstanceOf(String[].class).containsExactly("alpha", "bravo", "charlie");
    }
}
