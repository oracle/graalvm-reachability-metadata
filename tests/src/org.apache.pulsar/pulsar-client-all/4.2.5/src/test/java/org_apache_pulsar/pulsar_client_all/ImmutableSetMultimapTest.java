/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ImmutableSetMultimap;
import org.junit.jupiter.api.Test;

public class ImmutableSetMultimapTest {
    @Test
    void preservesDistinctValuesAcrossSerialization() throws Exception {
        final ImmutableSetMultimap<String, String> values = ImmutableSetMultimap.<String, String>builder()
                .put("topic", "one")
                .put("topic", "two")
                .put("topic", "one")
                .build();

        final ImmutableSetMultimap<String, String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.get("topic")).containsExactly("one", "two");
    }
}
