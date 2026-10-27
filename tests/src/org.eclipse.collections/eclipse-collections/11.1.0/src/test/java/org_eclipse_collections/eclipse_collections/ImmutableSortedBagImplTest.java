/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.api.bag.sorted.ImmutableSortedBag;
import org.eclipse.collections.impl.factory.SortedBags;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableSortedBagImplTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        ImmutableSortedBag<String> bag = SortedBags.immutable.with("gamma", "alpha", "beta", "alpha");
        String[] target = new String[0];

        String[] result = bag.toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .containsExactly("alpha", "alpha", "beta", "gamma");
        assertThat(result.getClass()).isEqualTo(String[].class);
    }
}
