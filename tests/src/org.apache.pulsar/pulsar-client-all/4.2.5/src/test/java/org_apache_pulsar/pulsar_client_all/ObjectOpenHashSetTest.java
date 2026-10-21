/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.junit.jupiter.api.Test;

public class ObjectOpenHashSetTest {
    @Test
    void roundTripPreservesHashSetValuesAndNull() throws Exception {
        ObjectOpenHashSet<String> input = new ObjectOpenHashSet<>();
        input.add("alpha");
        input.add("beta");
        input.add(null);

        ObjectOpenHashSet<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).containsExactlyInAnyOrder("alpha", "beta", null);
        assertThat(restored).isEqualTo(input);
    }
}
