/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TFloatObjectHashMap;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class TFloatObjectHashMapInnerMapBackedViewTest {
    @Test
    void valueCollectionToArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TFloatObjectHashMap<String> map = new TFloatObjectHashMap<>();
        map.put(1.5f, "alpha");
        map.put(2.5f, "beta");

        Collection<String> values = map.valueCollection();
        String[] result = values.toArray(new String[0]);

        assertThat(result).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(result.getClass()).isEqualTo(String[].class);
        assertThat(values.contains("alpha")).isTrue();
        assertThat(values.contains("beta")).isTrue();
    }
}
