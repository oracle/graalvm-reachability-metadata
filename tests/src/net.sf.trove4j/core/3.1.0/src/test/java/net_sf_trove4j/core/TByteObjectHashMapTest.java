/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TByteObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TByteObjectHashMapTest {
    @Test
    void valuesCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TByteObjectHashMap<String> map = new TByteObjectHashMap<>();

        map.put((byte) 1, "alpha");
        map.put((byte) 2, "beta");

        String[] values = map.values(new String[0]);

        assertThat(values).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(values.getClass()).isEqualTo(String[].class);
        assertThat(map.get((byte) 1)).isEqualTo("alpha");
        assertThat(map.get((byte) 2)).isEqualTo("beta");
    }
}
