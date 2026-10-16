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
    void preservesEnumMappingsAcrossSerialization() throws Exception {
        final EnumBiMap<Protocol, State> values = EnumBiMap.create(Protocol.class, State.class);
        values.put(Protocol.BINARY, State.READY);
        values.put(Protocol.HTTP, State.STARTING);

        final EnumBiMap<Protocol, State> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored).containsEntry(Protocol.BINARY, State.READY);
        assertThat(restored.inverse()).containsEntry(State.STARTING, Protocol.HTTP);
    }

    private enum Protocol {
        BINARY,
        HTTP
    }

    private enum State {
        STARTING,
        READY
    }
}
