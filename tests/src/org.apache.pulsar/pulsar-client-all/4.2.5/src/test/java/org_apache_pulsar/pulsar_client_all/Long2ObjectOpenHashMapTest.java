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
    void rebuildsHashedPrimitiveKeysDuringDeserialization() throws Exception {
        final Long2ObjectOpenHashMap<String> values = new Long2ObjectOpenHashMap<>();
        values.put(7L, "seven");
        values.put(11L, "eleven");

        final Long2ObjectOpenHashMap<String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.get(7L)).isEqualTo("seven");
        assertThat(restored.get(11L)).isEqualTo("eleven");
    }
}
