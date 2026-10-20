/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;

public class Long2ObjectOpenHashMapTest {
    @Test
    void roundTripPreservesPrimitiveKeyMappingsIncludingZero() throws Exception {
        Long2ObjectOpenHashMap<String> input = new Long2ObjectOpenHashMap<>();
        input.put(0L, "zero");
        input.put(42L, "answer");

        Long2ObjectOpenHashMap<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.get(0L)).isEqualTo("zero");
        assertThat(restored.get(42L)).isEqualTo("answer");
    }
}
