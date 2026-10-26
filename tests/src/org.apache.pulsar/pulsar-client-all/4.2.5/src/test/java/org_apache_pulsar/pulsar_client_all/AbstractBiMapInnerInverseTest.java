/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.BiMap;
import org.apache.pulsar.shade.com.google.common.collect.HashBiMap;
import org.junit.jupiter.api.Test;

public class AbstractBiMapInnerInverseTest {
    @Test
    void serializationPreservesInverseMappingsAndIdentity() throws Exception {
        HashBiMap<String, Integer> forward = HashBiMap.create();
        forward.put("one", 1);
        forward.put("two", 2);

        BiMap<Integer, String> restored = SerializationSupport.roundTrip(forward.inverse());

        assertThat(restored).containsEntry(1, "one").containsEntry(2, "two");
        assertThat(restored.inverse().inverse()).isSameAs(restored);
    }
}
