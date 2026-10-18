/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.longs.Long2ObjectAVLTreeMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class Long2ObjectAVLTreeMapTest {

    @Test
    void preservesADeepPrimitiveLongSearchTree() {
        final Long2ObjectAVLTreeMap<String> source = new Long2ObjectAVLTreeMap<>();
        for (long key = 1; key <= 12; key++) {
            source.put(key, "value-" + key);
        }

        final Long2ObjectAVLTreeMap<String> restored = SerializationUtils.clone(source);

        assertThat(restored.keySet()).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L);
        assertThat(restored.get(7L)).isEqualTo("value-7");
    }
}
