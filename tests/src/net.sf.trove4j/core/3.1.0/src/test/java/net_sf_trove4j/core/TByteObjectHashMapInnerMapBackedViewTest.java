/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.hash.TByteObjectHashMap;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class TByteObjectHashMapInnerMapBackedViewTest {
    @Test
    void valueCollectionToArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TByteObjectHashMap<String> map = new TByteObjectHashMap<>();
        map.put((byte) 1, "alpha");
        map.put((byte) 2, "beta");

        Collection<String> values = map.valueCollection();
        String[] result = values.toArray(new String[0]);

        assertThat(result).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(result.getClass()).isEqualTo(String[].class);
        assertThat(values.contains("alpha")).isTrue();
        assertThat(values.contains("beta")).isTrue();
    }
}
