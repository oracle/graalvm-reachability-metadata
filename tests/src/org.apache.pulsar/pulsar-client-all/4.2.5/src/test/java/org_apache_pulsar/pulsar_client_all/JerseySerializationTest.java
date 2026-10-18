/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.guava.HashMultimap;
import org.junit.jupiter.api.Test;

public class JerseySerializationTest {

    @Test
    void preservesJerseyMultimapKeysAndValues() {
        final HashMultimap<String, String> source = HashMultimap.create();
        source.put("orders", "created");
        source.put("orders", "stored");
        source.put("audit", "recorded");

        final HashMultimap<String, String> restored = SerializationUtils.clone(source);

        assertThat(restored.get("orders")).containsExactlyInAnyOrder("created", "stored");
        assertThat(restored.get("audit")).containsExactly("recorded");
    }
}
