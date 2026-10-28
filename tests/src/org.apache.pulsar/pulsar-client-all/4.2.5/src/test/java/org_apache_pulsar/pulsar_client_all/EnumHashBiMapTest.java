/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumHashBiMap;
import org.junit.jupiter.api.Test;

public class EnumHashBiMapTest {
    @Test
    void roundTripPreservesEnumKeysAndInverseLookup() throws Exception {
        EnumHashBiMap<Priority, String> input = EnumHashBiMap.create(Priority.class);
        input.put(Priority.LOW, "deferred");
        input.put(Priority.HIGH, "urgent");

        EnumHashBiMap<Priority, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.keyType()).isEqualTo(Priority.class);
        assertThat(restored.inverse().get("urgent")).isEqualTo(Priority.HIGH);
        assertThat(restored).containsEntry(Priority.LOW, "deferred");
    }

    private enum Priority {
        LOW,
        HIGH
    }
}
