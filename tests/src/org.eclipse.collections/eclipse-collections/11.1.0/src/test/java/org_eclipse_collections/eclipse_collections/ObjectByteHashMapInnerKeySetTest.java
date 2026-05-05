/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.eclipse.collections.impl.map.mutable.primitive.ObjectByteHashMap;
import org.junit.jupiter.api.Test;

public class ObjectByteHashMapInnerKeySetTest {
    @Test
    void toArrayCreatesTypedKeyArrayWhenSuppliedArrayIsTooSmall() {
        final ObjectByteHashMap<String> map = ObjectByteHashMap.newWithKeysValues(
                "alpha", (byte) 1,
                "bravo", (byte) 2,
                "charlie", (byte) 3);
        final Set<String> keySet = map.keySet();

        final String[] keys = keySet.toArray(new String[0]);

        assertThat(keys)
                .isExactlyInstanceOf(String[].class)
                .containsExactlyInAnyOrder("alpha", "bravo", "charlie");
    }
}
