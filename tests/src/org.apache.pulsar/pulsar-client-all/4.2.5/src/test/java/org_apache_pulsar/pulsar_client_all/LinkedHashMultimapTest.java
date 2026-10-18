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

public class LinkedHashMultimapTest {

    @Test
    void preservesKeyAndEntryInsertionOrder() {
        final LinkedHashMultimap<String, String> source = LinkedHashMultimap.create();
        source.put("primary", "one");
        source.put("primary", "two");
        source.put("secondary", "three");

        final LinkedHashMultimap<String, String> restored = SerializationUtils.clone(source);

        assertThat(restored.keySet()).containsExactly("primary", "secondary");
        assertThat(restored.get("primary")).containsExactly("one", "two");
        assertThat(restored.get("secondary")).containsExactly("three");
    }
}
