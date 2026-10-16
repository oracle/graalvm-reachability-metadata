/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectArraySet;
import org.junit.jupiter.api.Test;

public class ObjectArraySetTest {
    @Test
    void preservesDistinctValuesAndCreatesTypedArrays() throws Exception {
        final ObjectArraySet<String> values = ObjectArraySet.of("one", "two");
        final ObjectArraySet<String> restored = SerializationTestSupport.roundTrip(values);

        assertThat(restored).containsExactlyInAnyOrder("one", "two");
        assertThat(restored.toArray(new String[0])).containsExactlyInAnyOrder("one", "two");
    }
}
