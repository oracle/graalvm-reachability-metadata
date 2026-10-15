/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.TreeMultiset;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class TreeMultisetTest {
    @Test
    void preservesSortedElementCountsAcrossSerialization() {
        TreeMultiset<String> original = TreeMultiset.create();
        original.add("topic-b", 1);
        original.add("topic-a", 3);

        TreeMultiset<String> restored = SerializationUtils.roundtrip(original);

        assertThat(restored.elementSet()).containsExactly("topic-a", "topic-b");
        assertThat(restored.count("topic-a")).isEqualTo(3);
        assertThat(restored).isEqualTo(original);
    }
}
