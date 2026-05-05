/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.map.strategy.mutable.UnifiedMapWithHashingStrategy;
import org.junit.jupiter.api.Test;

public class UnifiedMapWithHashingStrategyInnerEntrySetTest {
    @Test
    void toArrayCreatesTypedEntryArrayWhenSuppliedArrayIsTooSmall() {
        final UnifiedMapWithHashingStrategy<String, Integer> map = UnifiedMapWithHashingStrategy.newWithKeysValues(
                HashingStrategies.defaultStrategy(),
                "alpha", 1,
                "bravo", 2,
                "charlie", 3);
        final Set<Map.Entry<String, Integer>> entrySet = map.entrySet();

        final Map.Entry<?, ?>[] entries = entrySet.toArray(new Map.Entry<?, ?>[0]);

        assertThat(entries)
                .isInstanceOf(Map.Entry[].class)
                .containsExactlyInAnyOrder(
                        new AbstractMap.SimpleImmutableEntry<>("alpha", 1),
                        new AbstractMap.SimpleImmutableEntry<>("bravo", 2),
                        new AbstractMap.SimpleImmutableEntry<>("charlie", 3));
    }
}
