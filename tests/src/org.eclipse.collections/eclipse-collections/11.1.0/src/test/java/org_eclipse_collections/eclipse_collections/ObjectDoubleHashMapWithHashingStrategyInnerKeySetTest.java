/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMapWithHashingStrategy;
import org.junit.jupiter.api.Test;

public class ObjectDoubleHashMapWithHashingStrategyInnerKeySetTest {
    @Test
    void toArrayCreatesTypedKeyArrayWhenSuppliedArrayIsTooSmall() {
        final ObjectDoubleHashMapWithHashingStrategy<String> map = ObjectDoubleHashMapWithHashingStrategy.newWithKeysValues(
                HashingStrategies.defaultStrategy(),
                "alpha", 1.0d,
                "bravo", 2.0d,
                "charlie", 3.0d);
        final Set<String> keySet = map.keySet();

        final String[] keys = keySet.toArray(new String[0]);

        assertThat(keys)
                .isExactlyInstanceOf(String[].class)
                .containsExactlyInAnyOrder("alpha", "bravo", "charlie");
    }
}
