/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.block.factory.HashingStrategies;
import org.eclipse.collections.impl.set.strategy.mutable.UnifiedSetWithHashingStrategy;
import org.junit.jupiter.api.Test;

public class UnifiedSetWithHashingStrategyTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final UnifiedSetWithHashingStrategy<String> set = UnifiedSetWithHashingStrategy.newSetWith(
                HashingStrategies.defaultStrategy(),
                "alpha",
                "bravo",
                "charlie");

        final String[] values = set.toArray(new String[0]);

        assertThat(values)
                .isExactlyInstanceOf(String[].class)
                .containsExactlyInAnyOrder("alpha", "bravo", "charlie");
    }
}
