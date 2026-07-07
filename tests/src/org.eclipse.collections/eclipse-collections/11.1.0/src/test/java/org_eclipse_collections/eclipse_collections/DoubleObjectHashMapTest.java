/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.map.mutable.primitive.DoubleObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DoubleObjectHashMapTest {

    @Test
    void toArrayAllocatesTypedArrayWhenProvidedArrayIsTooSmall() {
        DoubleObjectHashMap<String> map = DoubleObjectHashMap.newWithKeysValues(
                2.0d,
                "two",
                3.0d,
                "three",
                4.0d,
                "four");

        CharSequence[] values = map.toArray(new CharSequence[0]);

        assertThat(values.getClass()).isEqualTo(CharSequence[].class);
        assertThat(values).containsExactlyInAnyOrder("two", "three", "four");
    }
}
