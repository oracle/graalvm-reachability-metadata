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

public class TreeMultisetTest {

    @Test
    void preservesSortedElementsAndCounts() {
        final TreeMultiset<String> source = TreeMultiset.create();
        source.add("secondary", 2);
        source.add("primary", 3);

        final TreeMultiset<String> restored = SerializationUtils.clone(source);

        assertThat(restored.elementSet()).containsExactly("primary", "secondary");
        assertThat(restored.count("primary")).isEqualTo(3);
        assertThat(restored.count("secondary")).isEqualTo(2);
    }
}
