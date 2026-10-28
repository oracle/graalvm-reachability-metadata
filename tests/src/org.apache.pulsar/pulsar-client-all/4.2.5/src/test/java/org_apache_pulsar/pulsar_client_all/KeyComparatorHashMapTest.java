/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.Locale.ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.collection.KeyComparator;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.collection.KeyComparatorHashMap;
import org.junit.jupiter.api.Test;

public class KeyComparatorHashMapTest {
    @Test
    void roundTripPreservesCustomKeySemantics() throws Exception {
        KeyComparatorHashMap<String, Integer> input =
                new KeyComparatorHashMap<>(new CaseInsensitiveComparator());
        input.put("Persistent", 1);
        input.put("PERSISTENT", 2);

        KeyComparatorHashMap<String, Integer> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).hasSize(1);
        assertThat(restored.get("persistent")).isEqualTo(2);
    }

    private static final class CaseInsensitiveComparator implements KeyComparator<String> {
        @Override
        public boolean equals(String left, String right) {
            return left.equalsIgnoreCase(right);
        }

        @Override
        public int hash(String value) {
            return value.toLowerCase(ROOT).hashCode();
        }
    }
}
