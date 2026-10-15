/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.EnumHashBiMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class EnumHashBiMapTest {
    @Test
    void preservesEnumKeysAndInverseLookupAcrossSerialization() {
        EnumHashBiMap<Priority, String> original = EnumHashBiMap.create(Priority.class);
        original.put(Priority.HIGH, "urgent");
        original.put(Priority.NORMAL, "regular");

        EnumHashBiMap<Priority, String> restored = SerializationUtils.roundtrip(original);

        assertThat(restored).isEqualTo(original);
        assertThat(restored.inverse().get("urgent")).isEqualTo(Priority.HIGH);
    }

    private enum Priority {
        HIGH,
        NORMAL
    }
}
