/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.map.mutable.primitive.FloatObjectHashMap;
import org.junit.jupiter.api.Test;

public class FloatObjectHashMapTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final FloatObjectHashMap<String> map = new FloatObjectHashMap<>();
        map.put(0.0f, "zero");
        map.put(1.0f, "one");
        map.put(42.0f, "forty-two");

        final String[] values = map.toArray(new String[0]);

        assertThat(values)
                .isExactlyInstanceOf(String[].class)
                .containsExactlyInAnyOrder("zero", "one", "forty-two");
    }
}
