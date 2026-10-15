/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumBiMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class EnumBiMapTest {
    @Test
    void preservesBidirectionalEnumMappingsAcrossSerialization() {
        EnumBiMap<TopicState, DeliveryState> original =
                EnumBiMap.create(TopicState.class, DeliveryState.class);
        original.put(TopicState.ACTIVE, DeliveryState.READY);
        original.put(TopicState.INACTIVE, DeliveryState.PAUSED);

        EnumBiMap<TopicState, DeliveryState> restored =
                SerializationUtils.roundtrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.inverse().get(DeliveryState.READY)).isEqualTo(TopicState.ACTIVE);
    }

    private enum TopicState {
        ACTIVE,
        INACTIVE
    }

    private enum DeliveryState {
        READY,
        PAUSED
    }
}
