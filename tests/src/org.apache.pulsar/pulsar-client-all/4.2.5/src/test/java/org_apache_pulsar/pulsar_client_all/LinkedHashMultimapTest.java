/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.LinkedHashMultimap;
import org.junit.jupiter.api.Test;

public class LinkedHashMultimapTest {
    @Test
    void preservesInsertionOrderAcrossSerialization() throws Exception {
        final LinkedHashMultimap<String, String> values = LinkedHashMultimap.create();
        values.put("topic", "first");
        values.put("topic", "second");
        values.put("tenant", "acme");

        final LinkedHashMultimap<String, String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.get("topic")).containsExactly("first", "second");
        assertThat(restored.get("tenant")).containsExactly("acme");
    }
}
