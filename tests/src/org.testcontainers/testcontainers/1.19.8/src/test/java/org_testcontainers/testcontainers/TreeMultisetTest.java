/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.TreeMultiset;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeMultisetTest {
    @Test
    void preservesSortedCountsWhenSerialized() {
        TreeMultiset<String> values = TreeMultiset.create();
        values.add("second", 2);
        values.add("first", 1);
        TreeMultiset<String> restored = SerializationUtils.roundtrip(values);

        assertThat(restored.elementSet()).containsExactly("first", "second");
        assertThat(restored.count("second")).isEqualTo(2);
    }
}
