/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TCharObjectHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TCharObjectHashMapTest {
    @Test
    void valuesCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TCharObjectHashMap<String> map = new TCharObjectHashMap<>();

        map.put('a', "alpha");
        map.put('b', "beta");

        String[] values = map.values(new String[0]);

        assertThat(values).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(values.getClass()).isEqualTo(String[].class);
        assertThat(map.get('a')).isEqualTo("alpha");
        assertThat(map.get('b')).isEqualTo("beta");
    }
}
