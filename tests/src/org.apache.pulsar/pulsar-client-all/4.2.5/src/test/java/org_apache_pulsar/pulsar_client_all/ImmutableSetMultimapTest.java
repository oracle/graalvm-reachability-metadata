/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ImmutableSetMultimap;
import org.junit.jupiter.api.Test;

public class ImmutableSetMultimapTest {
    @Test
    void roundTripPreservesDistinctValues() throws Exception {
        ImmutableSetMultimap<String, String> input = ImmutableSetMultimap.<String, String>builder()
                .put("letters", "a")
                .put("letters", "b")
                .put("letters", "a")
                .put("numbers", "one")
                .build();

        ImmutableSetMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.get("letters")).containsExactlyInAnyOrder("a", "b");
        assertThat(restored.inverse().get("one")).containsExactly("numbers");
    }
}
