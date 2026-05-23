/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TLongObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TLongObjectHashMapTest {
    @Test
    void valuesCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TLongObjectHashMap<String> map = new TLongObjectHashMap<>();

        map.put(1L, "alpha");
        map.put(2L, "beta");

        String[] values = map.values(new String[0]);

        assertThat(values).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(values.getClass()).isEqualTo(String[].class);
        assertThat(map.get(1L)).isEqualTo("alpha");
        assertThat(map.get(2L)).isEqualTo("beta");
    }
}
