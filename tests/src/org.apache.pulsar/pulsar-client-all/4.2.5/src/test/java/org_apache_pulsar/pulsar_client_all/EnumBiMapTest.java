/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumBiMap;
import org.junit.jupiter.api.Test;

public class EnumBiMapTest {
    @Test
    void roundTripPreservesEnumTypesAndInverseMappings() throws Exception {
        EnumBiMap<Priority, Status> input = EnumBiMap.create(Priority.class, Status.class);
        input.put(Priority.LOW, Status.QUEUED);
        input.put(Priority.HIGH, Status.RUNNING);

        EnumBiMap<Priority, Status> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.keyType()).isSameAs(Priority.class);
        assertThat(restored.valueType()).isSameAs(Status.class);
        assertThat(restored.inverse().get(Status.RUNNING)).isSameAs(Priority.HIGH);
    }

    public enum Priority {
        LOW,
        HIGH
    }

    public enum Status {
        QUEUED,
        RUNNING
    }
}
