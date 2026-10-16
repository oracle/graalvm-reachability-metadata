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
    void preservesEnumKeysAcrossSerialization() throws Exception {
        final EnumHashBiMap<Protocol, String> values = EnumHashBiMap.create(Protocol.class);
        values.put(Protocol.BINARY, "pulsar");
        values.put(Protocol.HTTP, "admin");

        final EnumHashBiMap<Protocol, String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored).containsEntry(Protocol.BINARY, "pulsar").containsEntry(Protocol.HTTP, "admin");
        assertThat(restored.inverse().get("pulsar")).isEqualTo(Protocol.BINARY);
    }

    private enum Protocol {
        BINARY,
        HTTP
    }
}
