/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TObjectShortHashMap;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TObjectShortHashMapInnerMapBackedViewTest {
    @Test
    void keySetToArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TObjectShortHashMap<String> map = new TObjectShortHashMap<>();
        map.put("Alpha", (short) 1);
        map.put("Beta", (short) 2);

        Set<String> keys = map.keySet();
        String[] result = keys.toArray(new String[0]);

        assertThat(result).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(result.getClass()).isEqualTo(String[].class);
        assertThat(keys.contains("Alpha")).isTrue();
        assertThat(keys.contains("Beta")).isTrue();
    }
}
