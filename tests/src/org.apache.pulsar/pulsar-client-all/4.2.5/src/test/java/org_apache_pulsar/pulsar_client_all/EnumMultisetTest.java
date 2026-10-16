/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumMultiset;
import org.junit.jupiter.api.Test;

public class EnumMultisetTest {
    @Test
    void preservesEnumCountsAcrossSerialization() throws Exception {
        final EnumMultiset<State> values = EnumMultiset.create(State.class);
        values.add(State.READY, 4);
        values.add(State.STARTING, 1);

        final EnumMultiset<State> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.count(State.READY)).isEqualTo(4);
        assertThat(restored.count(State.STARTING)).isEqualTo(1);
    }

    private enum State {
        STARTING,
        READY
    }
}
