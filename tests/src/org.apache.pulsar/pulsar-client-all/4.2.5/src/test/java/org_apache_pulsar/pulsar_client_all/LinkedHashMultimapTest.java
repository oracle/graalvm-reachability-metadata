/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.LinkedHashMultimap;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void roundTripPreservesKeyAndEntryIterationOrder() throws Exception {
        LinkedHashMultimap<String, String> input = LinkedHashMultimap.create();
        input.put("letters", "a");
        input.put("numbers", "one");
        input.put("letters", "b");
        input.put("numbers", "two");

        LinkedHashMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.keySet()).containsExactly("letters", "numbers");
        assertThat(restored.entries()).containsExactly(
                entry("letters", "a"), entry("numbers", "one"), entry("letters", "b"), entry("numbers", "two"));
    }
}
