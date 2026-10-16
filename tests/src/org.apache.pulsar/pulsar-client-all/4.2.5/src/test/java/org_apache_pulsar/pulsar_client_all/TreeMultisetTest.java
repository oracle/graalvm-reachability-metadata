/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.TreeMultiset;
import org.junit.jupiter.api.Test;

public class TreeMultisetTest {
    @Test
    void preservesSortedCountsAcrossSerialization() throws Exception {
        final TreeMultiset<String> values = TreeMultiset.create();
        values.add("beta", 2);
        values.add("alpha", 1);

        final TreeMultiset<String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.elementSet()).containsExactly("alpha", "beta");
        assertThat(restored.count("beta")).isEqualTo(2);
    }
}
