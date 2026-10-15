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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class LinkedListMultimapTest {
    @Test
    void preservesDuplicateValuesAndEntryOrderAcrossSerialization() {
        LinkedListMultimap<String, String> original = LinkedListMultimap.create();
        original.put("topic", "first");
        original.put("other", "middle");
        original.put("topic", "first");

        LinkedListMultimap<String, String> restored = SerializationUtils.roundtrip(original);

        assertThat(restored.entries()).containsExactlyElementsOf(original.entries());
        assertThat(restored.get("topic")).containsExactly("first", "first");
    }
}
