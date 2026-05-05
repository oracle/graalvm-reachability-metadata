/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.set.mutable.UnifiedSet;
import org.junit.jupiter.api.Test;

public class UnifiedSetTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final UnifiedSet<String> set = UnifiedSet.newSetWith("alpha", "bravo", "charlie");

        final String[] result = set.toArray(new String[0]);

        assertThat(result)
                .isInstanceOf(String[].class)
                .containsExactlyInAnyOrder("alpha", "bravo", "charlie");
    }
}
