/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.THashMap;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class THashMapInnerMapBackedViewTest {
    @Test
    void keySetToArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        THashMap<String, Integer> map = new THashMap<>();
        map.put("Alpha", 1);
        map.put("Beta", 2);

        Set<String> keys = map.keySet();
        String[] result = keys.toArray(new String[0]);

        assertThat(result).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(result.getClass()).isEqualTo(String[].class);
        assertThat(keys.contains("Alpha")).isTrue();
        assertThat(keys.contains("Beta")).isTrue();
    }
}
