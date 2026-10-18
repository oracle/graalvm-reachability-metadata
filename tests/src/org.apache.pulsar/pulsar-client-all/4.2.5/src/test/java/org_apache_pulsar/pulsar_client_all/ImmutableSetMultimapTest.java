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

public class ImmutableSetMultimapTest {

    @Test
    void preservesUniqueImmutableValuesForEveryKey() {
        final ImmutableSetMultimap<String, String> source = ImmutableSetMultimap.<String, String>builder()
                .put("orders", "created")
                .put("orders", "created")
                .put("orders", "stored")
                .build();

        final ImmutableSetMultimap<String, String> restored = SerializationUtils.clone(source);

        assertThat(restored).isEqualTo(source);
        assertThat(restored.get("orders")).containsExactlyInAnyOrder("created", "stored");
    }
}
