/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeMap;

import org.eclipse.collections.impl.map.sorted.immutable.ImmutableTreeMap;
import org.junit.jupiter.api.Test;

public class ImmutableTreeMapInnerImmutableSortedMapKeySetTest {
    @Test
    void toArrayCreatesTypedKeyArrayWhenSuppliedArrayIsTooSmall() {
        final TreeMap<String, Integer> sortedMap = new TreeMap<>();
        sortedMap.put("charlie", 3);
        sortedMap.put("alpha", 1);
        sortedMap.put("bravo", 2);
        final ImmutableTreeMap<String, Integer> immutableMap = new ImmutableTreeMap<>(sortedMap);
        final Set<String> keySet = immutableMap.keySet();

        final String[] keys = keySet.toArray(new String[0]);

        assertThat(keys)
                .isInstanceOf(String[].class)
                .containsExactly("alpha", "bravo", "charlie");
    }
}
