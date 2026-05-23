/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TObjectByteHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TObjectByteHashMapTest {
    @Test
    void keysCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TObjectByteHashMap<String> map = new TObjectByteHashMap<>();

        map.put("Alpha", (byte) 1);
        map.put("Beta", (byte) 2);

        String[] keys = map.keys(new String[0]);

        assertThat(keys).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(keys.getClass()).isEqualTo(String[].class);
        assertThat(map.get("Alpha")).isEqualTo((byte) 1);
        assertThat(map.get("Beta")).isEqualTo((byte) 2);
    }
}
