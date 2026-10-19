/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.CompactHashSet;
import org.junit.jupiter.api.Test;

public class CompactHashSetTest {
    @Test
    void roundTripPreservesElementsAndMutability() throws Exception {
        CompactHashSet<String> input = CompactHashSet.create("alpha", "beta");

        CompactHashSet<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).containsExactlyInAnyOrder("alpha", "beta");
        assertThat(restored.add("gamma")).isTrue();
        assertThat(restored).contains("gamma");
    }
}
