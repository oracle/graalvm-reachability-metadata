/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static java.util.Comparator.reverseOrder;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.com.google.common.collect.TreeMultiset;
import org.junit.jupiter.api.Test;

public class TreeMultisetTest {
    @Test
    void roundTripPreservesComparatorAndCounts() throws Exception {
        TreeMultiset<String> input = TreeMultiset.create(reverseOrder());
        input.add("alpha", 2);
        input.add("omega", 1);

        TreeMultiset<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.count("alpha")).isEqualTo(2);
        assertThat(restored.elementSet()).containsExactly("omega", "alpha");
        restored.add("zeta");
        assertThat(restored.elementSet()).containsExactly("zeta", "omega", "alpha");
    }
}
