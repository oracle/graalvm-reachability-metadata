/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.set.hash.THashSet;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class THashSetTest {
    @Test
    void toArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        THashSet<String> set = new THashSet<>();
        set.add("Alpha");
        set.add("Beta");

        String[] result = set.toArray(new String[0]);

        assertThat(result).containsExactlyInAnyOrder("Alpha", "Beta");
        assertThat(result.getClass()).isEqualTo(String[].class);
        assertThat(set.contains("Alpha")).isTrue();
        assertThat(set.contains("Beta")).isTrue();
    }
}
