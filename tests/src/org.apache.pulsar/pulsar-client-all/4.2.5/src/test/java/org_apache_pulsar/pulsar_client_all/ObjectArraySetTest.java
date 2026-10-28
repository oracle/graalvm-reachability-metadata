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
    void roundTripAndTypedArrayPreserveSetValues() throws Exception {
        ObjectArraySet<String> input = new ObjectArraySet<>();
        input.add("alpha");
        input.add("beta");

        ObjectArraySet<String> restored = SerializationSupport.roundTrip(input);
        String[] values = restored.toArray(new String[0]);

        assertThat(restored).containsExactly("alpha", "beta");
        assertThat(values).containsExactly("alpha", "beta");
    }
}
