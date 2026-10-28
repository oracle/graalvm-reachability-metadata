/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.junit.jupiter.api.Test;

public class Long2ObjectAVLTreeMapTest {
    @Test
    void roundTripPreservesSortedPrimitiveKeysAndValues() throws Exception {
        Long2ObjectAVLTreeMap<String> input = new Long2ObjectAVLTreeMap<>();
        input.put(30L, "thirty");
        input.put(10L, "ten");
        input.put(20L, "twenty");

        Long2ObjectAVLTreeMap<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).isEqualTo(input);
        assertThat(restored.keySet()).containsExactly(10L, 20L, 30L);
        assertThat(restored.get(20L)).isEqualTo("twenty");
    }

    @Test
    void roundTripPreservesTwoEntryTreeBranch() throws Exception {
        Long2ObjectAVLTreeMap<String> input = new Long2ObjectAVLTreeMap<>();
        input.put(2L, "two");
        input.put(1L, "one");

        Long2ObjectAVLTreeMap<String> restored = SerializationSupport.roundTrip(input);

        assertThat(restored.keySet()).containsExactly(1L, 2L);
        assertThat(restored.values()).containsExactly("one", "two");
    }
}
