/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TObjectLongHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TObjectLongHashMapTest {
    @Test
    void keysCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TObjectLongHashMap<String> map = new TObjectLongHashMap<>();

        map.put("Alpha", 1L);
        map.put("Beta", 2L);

        String[] keys = map.keys(new String[0]);

        assertThat(keys).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(keys.getClass()).isEqualTo(String[].class);
        assertThat(map.get("Alpha")).isEqualTo(1L);
        assertThat(map.get("Beta")).isEqualTo(2L);
    }
}
