/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.util.AbstractMap;
import java.util.Map;

import org.eclipse.collections.impl.map.mutable.UnifiedMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UnifiedMapInnerEntrySetTest {

    @Test
    @SuppressWarnings("unchecked")
    void createsTypedEntryArrayWhenTargetArrayIsTooSmall() {
        UnifiedMap<String, Integer> map = UnifiedMap.newWithKeysValues("one", 1, "two", 2);
        Map.Entry<String, Integer>[] target = new Map.Entry[1];

        Map.Entry<String, Integer>[] result = map.entrySet().toArray(target);

        assertThat(result)
                .isNotSameAs(target)
                .isInstanceOf(Map.Entry[].class)
                .containsExactlyInAnyOrder(
                        new AbstractMap.SimpleImmutableEntry<>("one", 1),
                        new AbstractMap.SimpleImmutableEntry<>("two", 2));
    }
}
