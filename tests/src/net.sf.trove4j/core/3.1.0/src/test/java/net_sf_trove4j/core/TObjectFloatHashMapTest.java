/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TObjectFloatHashMap;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TObjectFloatHashMapTest {
    @Test
    void keysCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TObjectFloatHashMap<String> map = new TObjectFloatHashMap<>();

        map.put("Alpha", 1.25F);
        map.put("Beta", 2.5F);

        String[] keys = map.keys(new String[0]);

        assertThat(keys).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(keys.getClass()).isEqualTo(String[].class);
        assertThat(map.get("Alpha")).isEqualTo(1.25F);
        assertThat(map.get("Beta")).isEqualTo(2.5F);
    }
}
