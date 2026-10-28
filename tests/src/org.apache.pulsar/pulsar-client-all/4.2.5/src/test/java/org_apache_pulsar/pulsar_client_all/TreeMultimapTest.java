/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.TreeMultimap;
import org.junit.jupiter.api.Test;

public class TreeMultimapTest {
    @Test
    void roundTripPreservesComparatorsKeysAndValues() throws Exception {
        TreeMultimap<String, String> input = TreeMultimap.create(reverseOrder(), reverseOrder());
        input.put("letters", "a");
        input.put("letters", "c");
        input.put("letters", "b");
        input.put("numbers", "one");

        TreeMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.keySet()).containsExactly("numbers", "letters");
        assertThat(restored.get("letters")).containsExactly("c", "b", "a");
        restored.put("greek", "omega");
        assertThat(restored.keySet()).containsExactly("numbers", "letters", "greek");
    }
}
