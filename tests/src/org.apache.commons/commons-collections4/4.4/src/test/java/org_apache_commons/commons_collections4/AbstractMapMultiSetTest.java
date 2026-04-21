/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.multiset.HashMultiSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractMapMultiSetTest {

    @Test
    void createsTypedArrayWhenTargetArrayIsTooSmall() {
        HashMultiSet<String> multiSet = new HashMultiSet<>();
        multiSet.add("alpha", 2);
        multiSet.add("beta", 1);

        String[] target = new String[1];
        String[] values = multiSet.toArray(target);

        assertThat(values)
                .isNotSameAs(target)
                .hasSize(3)
                .containsExactlyInAnyOrder("alpha", "alpha", "beta");
    }

}
