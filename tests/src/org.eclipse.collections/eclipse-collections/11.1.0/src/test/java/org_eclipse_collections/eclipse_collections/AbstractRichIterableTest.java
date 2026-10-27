/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.bag.mutable.HashBag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractRichIterableTest {

    @Test
    void createsTypedArrayForInheritedBagImplementationWhenTargetArrayIsTooSmall() {
        HashBag<String> bag = HashBag.newBagWith("alpha", "beta", "alpha");
        String[] target = new String[1];

        String[] result = bag.toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .isInstanceOf(String[].class)
                .containsExactlyInAnyOrder("alpha", "alpha", "beta");
    }
}
