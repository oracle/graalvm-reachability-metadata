/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.collections.impl.map.mutable.primitive.ByteObjectHashMap;
import org.junit.jupiter.api.Test;

public class ByteObjectHashMapTest {
    @Test
    void toArrayCreatesTypedArrayWhenSuppliedArrayIsTooSmall() {
        final ByteObjectHashMap<String> map = new ByteObjectHashMap<>();
        map.put((byte) 0, "zero");
        map.put((byte) 1, "one");
        map.put((byte) 42, "forty-two");

        final String[] values = map.toArray(new String[0]);

        assertThat(values)
                .isExactlyInstanceOf(String[].class)
                .containsExactlyInAnyOrder("zero", "one", "forty-two");
    }
}
