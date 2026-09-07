/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.TreeMultimap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class TreeMultimapTest {
    @Test
    void preservesSortedEntriesWhenSerialized() {
        TreeMultimap<String, String> values = TreeMultimap.create();
        values.put("key", "two");
        values.put("key", "one");

        assertThat(SerializationUtils.roundtrip(values).get("key")).containsExactly("one", "two");
    }
}
