/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.map.custom_hash.TObjectLongCustomHashMap;
import gnu.trove.strategy.HashingStrategy;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

public class TObjectLongCustomHashMapTest {
    @Test
    void keysCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        TObjectLongCustomHashMap<String> map = new TObjectLongCustomHashMap<>(new CaseInsensitiveStrategy());

        map.put("Alpha", 1L);
        map.put("BETA", 2L);

        String[] keys = map.keys(new String[0]);

        assertThat(keys).containsExactlyInAnyOrder("Alpha", "BETA");
        assertThat(keys.getClass()).isEqualTo(String[].class);
        assertThat(map.get("alpha")).isEqualTo(1L);
        assertThat(map.get("beta")).isEqualTo(2L);
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
