/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.list.mutable.FastList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FastListTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        FastList<String> list = FastList.newListWith("alpha", "beta", "gamma");
        String[] target = new String[1];

        String[] result = list.toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .containsExactly("alpha", "beta", "gamma");
        assertThat(result.getClass()).isEqualTo(String[].class);
    }

    @Test
    void createsTypedArrayFromElementClass() {
        FastList<String> list = FastList.newListWith("alpha", "beta", "gamma");

        String[] result = list.toTypedArray(String.class);

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma");
    }
}
