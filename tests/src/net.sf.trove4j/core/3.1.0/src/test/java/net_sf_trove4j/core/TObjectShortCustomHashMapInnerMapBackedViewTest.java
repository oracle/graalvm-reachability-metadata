/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.custom_hash.TObjectShortCustomHashMap;
import gnu.trove.strategy.HashingStrategy;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class TObjectShortCustomHashMapInnerMapBackedViewTest {
    @Test
    void keySetToArrayCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TObjectShortCustomHashMap<String> map = new TObjectShortCustomHashMap<>(new CaseInsensitiveStrategy());
        map.put("Alpha", (short) 1);
        map.put("BETA", (short) 2);

        Set<String> keys = map.keySet();
        String[] result = keys.toArray(new String[0]);

        assertThat(result).containsExactlyInAnyOrder("Alpha", "BETA");
        assertThat(result.getClass()).isEqualTo(String[].class);
        assertThat(keys.contains("alpha")).isTrue();
        assertThat(keys.contains("beta")).isTrue();
    }

    private static final class CaseInsensitiveStrategy implements HashingStrategy<String> {
        private static final long serialVersionUID = 1L;

        @Override
        public int computeHashCode(String object) {
            return object.toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public boolean equals(String first, String second) {
            return first.equalsIgnoreCase(second);
        }
    }
}
