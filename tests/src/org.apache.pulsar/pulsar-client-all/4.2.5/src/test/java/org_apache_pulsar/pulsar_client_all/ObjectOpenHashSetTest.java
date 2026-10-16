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
    void rebuildsHashedValuesDuringDeserialization() throws Exception {
        final ObjectOpenHashSet<String> values = ObjectOpenHashSet.of("one", "two", "three");
        final ObjectOpenHashSet<String> restored = SerializationTestSupport.roundTrip(values);

        assertThat(restored).containsExactlyInAnyOrder("one", "two", "three");
    }
}
