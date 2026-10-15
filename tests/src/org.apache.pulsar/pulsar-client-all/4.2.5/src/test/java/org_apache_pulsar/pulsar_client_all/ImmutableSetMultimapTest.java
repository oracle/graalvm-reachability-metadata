/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ImmutableSetMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class ImmutableSetMultimapTest {
    @Test
    void preservesImmutableMappingsAcrossSerialization() {
        ImmutableSetMultimap<String, Integer> original =
                ImmutableSetMultimap.<String, Integer>builder()
                        .put("topic-a", 1)
                        .put("topic-a", 2)
                        .put("topic-b", 3)
                        .build();

        ImmutableSetMultimap<String, Integer> restored =
                SerializationUtils.roundtrip(original);

        assertThat(restored).isEqualTo(original).isNotSameAs(original);
        assertThat(restored.get("topic-a")).containsExactlyInAnyOrder(1, 2);
    }
}
