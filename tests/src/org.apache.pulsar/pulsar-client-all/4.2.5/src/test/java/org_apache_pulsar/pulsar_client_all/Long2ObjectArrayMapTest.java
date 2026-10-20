/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.junit.jupiter.api.Test;

public class Long2ObjectArrayMapTest {
    @Test
    void roundTripPreservesPrimitiveKeysAndInsertionOrder() throws Exception {
        Long2ObjectArrayMap<String> input = new Long2ObjectArrayMap<>();
        input.put(4L, "four");
        input.put(2L, "two");

        Long2ObjectArrayMap<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.keySet()).containsExactly(4L, 2L);
        assertThat(restored.get(2L)).isEqualTo("two");
    }
}
