/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.map.strategy.mutable.UnifiedMapWithHashingStrategy;
import org.junit.jupiter.api.Test;

public class UnifiedMapWithHashingStrategyInnerValuesCollectionTest {
    @Test
    void toArrayCreatesTypedValueArrayWhenSuppliedArrayIsTooSmall() {
        final UnifiedMapWithHashingStrategy<String, Integer> map = UnifiedMapWithHashingStrategy.newWithKeysValues(
                HashingStrategies.defaultStrategy(),
                "alpha", 1,
                "bravo", 2,
                "charlie", 3);
        final Collection<Integer> values = map.values();

        final Integer[] valueArray = values.toArray(new Integer[0]);

        assertThat(valueArray)
                .isExactlyInstanceOf(Integer[].class)
                .containsExactlyInAnyOrder(1, 2, 3);
    }
}
