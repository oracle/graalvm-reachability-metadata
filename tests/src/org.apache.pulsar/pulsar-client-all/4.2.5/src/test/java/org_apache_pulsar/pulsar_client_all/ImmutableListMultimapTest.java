/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ImmutableListMultimap;
import org.junit.jupiter.api.Test;

public class ImmutableListMultimapTest {
    @Test
    void preservesDuplicateValuesAcrossSerialization() throws Exception {
        final ImmutableListMultimap<String, String> values = ImmutableListMultimap.<String, String>builder()
                .put("topic", "one")
                .put("topic", "one")
                .put("topic", "two")
                .build();

        final ImmutableListMultimap<String, String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.get("topic")).containsExactly("one", "one", "two");
    }
}
