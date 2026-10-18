/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.ImmutableListMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class ImmutableListMultimapTest {

    @Test
    void preservesImmutableListsForEveryKey() {
        final ImmutableListMultimap<String, String> source = ImmutableListMultimap.<String, String>builder()
                .put("orders", "created")
                .put("orders", "stored")
                .put("audit", "recorded")
                .build();

        final ImmutableListMultimap<String, String> restored = SerializationUtils.clone(source);

        assertThat(restored).isEqualTo(source);
        assertThat(restored.get("orders")).containsExactly("created", "stored");
    }
}
