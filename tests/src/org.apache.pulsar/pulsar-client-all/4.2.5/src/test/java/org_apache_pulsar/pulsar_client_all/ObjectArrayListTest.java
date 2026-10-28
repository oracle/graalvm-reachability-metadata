/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.Test;

public class ObjectArrayListTest {
    @Test
    void roundTripAndTypedArrayPreserveListValues() throws Exception {
        ObjectArrayList<String> input = new ObjectArrayList<>();
        input.add("alpha");
        input.add("beta");

        ObjectArrayList<String> restored = SerializationSupport.roundTrip(input);
        String[] values = restored.toArray(new String[0]);

        assertThat(restored.size()).isEqualTo(2);
        assertThat(restored.get(0)).isEqualTo("alpha");
        assertThat(restored.get(1)).isEqualTo("beta");
        assertThat(values).containsExactly("alpha", "beta");
    }
}
