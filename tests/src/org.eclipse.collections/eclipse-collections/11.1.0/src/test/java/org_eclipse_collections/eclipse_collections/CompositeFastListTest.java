/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.list.mutable.CompositeFastList;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeFastListTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        CompositeFastList<String> list = new CompositeFastList<>();
        list.addAll(FastList.newListWith("alpha", "beta"));
        list.addAll(FastList.newListWith("gamma"));
        String[] target = new String[1];

        Object[] result = list.toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "beta", "gamma");
    }
}
