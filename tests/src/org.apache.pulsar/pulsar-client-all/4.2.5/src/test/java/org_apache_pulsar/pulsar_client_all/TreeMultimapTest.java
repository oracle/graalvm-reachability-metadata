/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.TreeMultimap;
import org.junit.jupiter.api.Test;

public class TreeMultimapTest {
    @Test
    void preservesSortedKeysAndValuesAcrossSerialization() throws Exception {
        final TreeMultimap<String, Integer> values = TreeMultimap.create();
        values.put("topic", 3);
        values.put("topic", 1);
        values.put("topic", 2);

        final TreeMultimap<String, Integer> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.get("topic")).containsExactly(1, 2, 3);
    }
}
