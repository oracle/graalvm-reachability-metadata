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
    void rebuildsADeepSortedTreeDuringDeserialization() throws Exception {
        final Long2ObjectAVLTreeMap<String> values = new Long2ObjectAVLTreeMap<>();
        for (long index = 0; index < 16; index++) {
            values.put(index, "value-" + index);
        }

        final Long2ObjectAVLTreeMap<String> restored = SerializationTestSupport.roundTrip(values);
        assertThat(restored.keySet()).containsExactly(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L,
                8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        assertThat(restored.get(12L)).isEqualTo("value-12");
    }
}
