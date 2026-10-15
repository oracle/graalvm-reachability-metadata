/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.TreeMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class TreeMultimapTest {
    @Test
    void preservesSortedKeysAndValuesAcrossSerialization() {
        TreeMultimap<String, Integer> original = TreeMultimap.create();
        original.put("topic-b", 3);
        original.put("topic-a", 2);
        original.put("topic-a", 1);

        TreeMultimap<String, Integer> restored = SerializationUtils.roundtrip(original);

        assertThat(restored.keySet()).containsExactly("topic-a", "topic-b");
        assertThat(restored.get("topic-a")).containsExactly(1, 2);
        assertThat(restored).isEqualTo(original);
    }
}
