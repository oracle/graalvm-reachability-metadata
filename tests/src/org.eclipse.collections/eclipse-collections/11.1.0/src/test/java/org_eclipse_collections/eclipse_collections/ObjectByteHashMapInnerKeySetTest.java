/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.util.Set;

import org.eclipse.collections.impl.map.mutable.primitive.ObjectByteHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectByteHashMapInnerKeySetTest {

    @Test
    void createsTypedKeyArrayWhenTargetArrayIsTooSmall() {
        ObjectByteHashMap<String> map = ObjectByteHashMap.newWithKeysValues(
                "alpha",
                (byte) 1,
                "beta",
                (byte) 2,
                "gamma",
                (byte) 3);
        Set<String> keys = map.keySet();

        CharSequence[] result = keys.toArray(new CharSequence[0]);

        assertThat(result.getClass()).isEqualTo(CharSequence[].class);
        assertThat(result).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }
}
