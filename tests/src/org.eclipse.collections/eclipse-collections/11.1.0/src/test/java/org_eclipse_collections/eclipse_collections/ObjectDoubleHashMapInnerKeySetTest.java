/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_collections.eclipse_collections;

import java.util.Set;

import org.eclipse.collections.impl.map.mutable.primitive.ObjectDoubleHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectDoubleHashMapInnerKeySetTest {

    @Test
    void createsTypedKeyArrayWhenTargetArrayIsTooSmall() {
        ObjectDoubleHashMap<String> map = ObjectDoubleHashMap.newWithKeysValues(
                "alpha",
                1.0d,
                "beta",
                2.0d,
                "gamma",
                3.0d);
        Set<String> keys = map.keySet();

        CharSequence[] result = keys.toArray(new CharSequence[0]);

        assertThat(result.getClass()).isEqualTo(CharSequence[].class);
        assertThat(result).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }
}
