/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnifiedMapInnerValuesCollectionTest {
    @Test
    void valuesToArrayAllocatesTypedArrayWhenProvidedArrayIsTooSmall() {
        UnifiedMap<String, Integer> map = UnifiedMap.newWithKeysValues("one", 1, "two", 2, "three", 3);
        Integer[] provided = new Integer[0];

        Integer[] result = map.values().toArray(provided);

        assertThat(result)
                .isNotSameAs(provided)
                .isInstanceOf(Integer[].class)
                .containsExactlyInAnyOrder(1, 2, 3);
    }
}
