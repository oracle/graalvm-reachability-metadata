/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import org.eclipse.collections.impl.map.mutable.primitive.IntObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntObjectHashMapTest {

    @Test
    void toArrayAllocatesTypedArrayWhenProvidedArrayIsTooSmall() {
        IntObjectHashMap<String> map = IntObjectHashMap.newWithKeysValues(
                2,
                "two",
                3,
                "three",
                4,
                "four");

        CharSequence[] values = map.toArray(new CharSequence[0]);

        assertThat(values.getClass()).isEqualTo(CharSequence[].class);
        assertThat(values).containsExactlyInAnyOrder("two", "three", "four");
    }
}
