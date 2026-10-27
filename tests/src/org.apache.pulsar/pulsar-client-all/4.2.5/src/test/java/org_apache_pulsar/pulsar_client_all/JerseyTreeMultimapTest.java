/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.org.glassfish.jersey.internal.guava.TreeMultimap;
import org.junit.jupiter.api.Test;

public class JerseyTreeMultimapTest {
    @Test
    void roundTripPreservesSortedKeysAndValues() throws Exception {
        TreeMultimap<String, String> input = TreeMultimap.create();
        input.put("letters", "c");
        input.put("letters", "a");
        input.put("numbers", "one");

        TreeMultimap<String, String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.asMap()).isEqualTo(input.asMap());
        assertThat(restored.get("letters")).containsExactly("a", "c");
    }
}
