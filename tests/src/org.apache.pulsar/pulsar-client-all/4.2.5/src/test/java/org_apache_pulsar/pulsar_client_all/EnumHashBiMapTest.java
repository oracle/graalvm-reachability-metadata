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

public class EnumHashBiMapTest {

    @Test
    void preservesEnumKeyTypeAndInverseLookup() {
        final EnumHashBiMap<Priority, String> source = EnumHashBiMap.create(Priority.class);
        source.put(Priority.NORMAL, "orders");
        source.put(Priority.HIGH, "alerts");

        final EnumHashBiMap<Priority, String> restored = SerializationUtils.clone(source);

        assertThat(restored.keyType()).isEqualTo(Priority.class);
        assertThat(restored.inverse().get("alerts")).isEqualTo(Priority.HIGH);
    }

    private enum Priority {
        NORMAL,
        HIGH
    }
}
