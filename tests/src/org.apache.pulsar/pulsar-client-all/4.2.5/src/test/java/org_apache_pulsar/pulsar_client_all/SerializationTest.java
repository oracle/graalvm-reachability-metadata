/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.HashMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class SerializationTest {
    @Test
    void preservesKeysAndValuesInSerializableMultimap() {
        HashMultimap<String, String> original = HashMultimap.create();
        original.put("persistent", "topic-a");
        original.put("persistent", "topic-b");
        original.put("non-persistent", "topic-c");

        HashMultimap<String, String> restored = SerializationUtils.roundtrip(original);

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.get("persistent")).containsExactlyInAnyOrder("topic-a", "topic-b");
    }
}
