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
    void roundTripPreservesEnumCounts() throws Exception {
        EnumMultiset<State> input = EnumMultiset.create(State.class);
        input.add(State.READY, 2);
        input.add(State.DONE, 1);

        EnumMultiset<State> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.count(State.READY)).isEqualTo(2);
        assertThat(restored.count(State.DONE)).isEqualTo(1);
        restored.add(State.DONE);
        assertThat(restored.count(State.DONE)).isEqualTo(2);
    }

    private enum State {
        READY,
        DONE
    }
}
