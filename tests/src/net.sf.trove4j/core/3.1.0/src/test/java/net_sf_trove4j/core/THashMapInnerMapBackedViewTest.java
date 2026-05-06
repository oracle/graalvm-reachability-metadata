/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.THashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class THashMapInnerMapBackedViewTest {
    @Test
    void keySetToArrayAllocatesTypedArrayWhenProvidedArrayIsTooSmall() {
        THashMap<String, Integer> map = new THashMap<>();
        map.put("Alpha", 1);
        map.put("Beta", 2);
        Set<String> keyView = map.keySet();

        String[] keys = keyView.toArray(new String[0]);

        assertThat(keys).isInstanceOf(String[].class);
        assertThat(keys).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(keyView.contains("Alpha")).isTrue();
        assertThat(keyView.contains("Beta")).isTrue();
    }
}
