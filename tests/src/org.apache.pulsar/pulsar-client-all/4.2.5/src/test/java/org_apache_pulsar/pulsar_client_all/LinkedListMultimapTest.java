/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.LinkedListMultimap;
import org.junit.jupiter.api.Test;

public class LinkedListMultimapTest {
    @Test
    void roundTripPreservesGlobalAndPerKeyEntryOrder() throws Exception {
        LinkedListMultimap<String, Integer> input = LinkedListMultimap.create();
        input.put("first", 1);
        input.put("second", 20);
        input.put("first", 2);
        input.put("second", 30);

        LinkedListMultimap<String, Integer> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.entries()).containsExactly(
                entry("first", 1), entry("second", 20), entry("first", 2), entry("second", 30));
        assertThat(restored.get("first")).containsExactly(1, 2);
    }
}
