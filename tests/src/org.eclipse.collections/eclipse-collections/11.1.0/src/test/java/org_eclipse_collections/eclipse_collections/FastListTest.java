/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.list.mutable.FastList;
import org.junit.jupiter.api.Test;

public class FastListTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final FastList<String> list = FastList.newListWith("alpha", "bravo", "charlie");

        final String[] result = list.toArray(new String[0]);

        assertThat(result).containsExactly("alpha", "bravo", "charlie");
    }

    @Test
    void toTypedArrayCreatesArrayForRequestedComponentType() {
        final FastList<String> list = FastList.newListWith("delta", "echo");

        final String[] result = list.toTypedArray(String.class);

        assertThat(result).isInstanceOf(String[].class).containsExactly("delta", "echo");
    }
}
