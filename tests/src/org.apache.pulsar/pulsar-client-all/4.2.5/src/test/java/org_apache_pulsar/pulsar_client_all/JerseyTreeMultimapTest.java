/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.guava.TreeMultimap;
import org.junit.jupiter.api.Test;

public class JerseyTreeMultimapTest {

    @Test
    void preservesJerseySortedKeysAndValues() {
        final TreeMultimap<String, Integer> source = TreeMultimap.create();
        source.put("secondary", 3);
        source.put("primary", 2);
        source.put("primary", 1);

        final TreeMultimap<String, Integer> restored = SerializationUtils.clone(source);

        assertThat(restored.keySet()).containsExactly("primary", "secondary");
        assertThat(restored.get("primary")).containsExactly(1, 2);
    }
}
