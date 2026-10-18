/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumMultiset;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class EnumMultisetTest {

    @Test
    void preservesCountsForEachDeliveryState() {
        final EnumMultiset<DeliveryState> source = EnumMultiset.create(DeliveryState.class);
        source.add(DeliveryState.RECEIVED, 5);
        source.add(DeliveryState.ACKNOWLEDGED, 3);

        final EnumMultiset<DeliveryState> restored = SerializationUtils.clone(source);

        assertThat(restored.count(DeliveryState.RECEIVED)).isEqualTo(5);
        assertThat(restored.count(DeliveryState.ACKNOWLEDGED)).isEqualTo(3);
    }

    private enum DeliveryState {
        RECEIVED,
        ACKNOWLEDGED
    }
}
