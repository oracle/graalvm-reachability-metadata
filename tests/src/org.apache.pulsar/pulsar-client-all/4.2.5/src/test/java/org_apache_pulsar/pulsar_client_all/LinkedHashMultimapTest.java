/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.LinkedHashMultimap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class LinkedHashMultimapTest {
    @Test
    void preservesGlobalEntryOrderAcrossSerialization() {
        LinkedHashMultimap<String, Integer> original = LinkedHashMultimap.create();
        original.put("partition-b", 2);
        original.put("partition-a", 1);
        original.put("partition-b", 3);

        LinkedHashMultimap<String, Integer> restored = SerializationUtils.roundtrip(original);

        assertThat(restored.entries()).containsExactlyElementsOf(original.entries());
        assertThat(restored).isNotSameAs(original);
    }
}
