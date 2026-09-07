/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.LinkedHashMultimap;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class LinkedHashMultimapTest {
    @Test
    void preservesEntriesAndInsertionOrderWhenSerialized() {
        LinkedHashMultimap<String, String> values = LinkedHashMultimap.create();
        values.put("key", "first");
        values.put("key", "second");

        assertThat(SerializationUtils.roundtrip(values).get("key")).containsExactly("first", "second");
    }
}
