/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.LinkedListMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class LinkedListMultimapTest {

    @Test
    void preservesRepeatedKeysAndGlobalEntryOrder() {
        final LinkedListMultimap<String, String> source = LinkedListMultimap.create();
        source.put("orders", "created");
        source.put("audit", "recorded");
        source.put("orders", "stored");

        final LinkedListMultimap<String, String> restored = SerializationUtils.clone(source);

        assertThat(restored.entries()).containsExactlyElementsOf(source.entries());
        assertThat(restored.get("orders")).containsExactly("created", "stored");
    }
}
