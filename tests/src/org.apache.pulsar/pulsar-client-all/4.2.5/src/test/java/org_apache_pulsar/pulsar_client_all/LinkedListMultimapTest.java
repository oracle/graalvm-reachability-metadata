/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.LinkedListMultimap;
import org.junit.jupiter.api.Test;

public class LinkedListMultimapTest {
    @Test
    void preservesGlobalAndPerKeyOrderAcrossSerialization() throws Exception {
        final LinkedListMultimap<String, String> values = LinkedListMultimap.create();
        values.put("topic", "one");
        values.put("tenant", "acme");
        values.put("topic", "two");

        final LinkedListMultimap<String, String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.get("topic")).containsExactly("one", "two");
        assertThat(restored.values()).containsExactly("one", "acme", "two");
    }
}
