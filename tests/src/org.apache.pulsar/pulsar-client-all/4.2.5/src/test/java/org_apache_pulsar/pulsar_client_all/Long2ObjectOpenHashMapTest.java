/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class Long2ObjectOpenHashMapTest {

    @Test
    void preservesPrimitiveLongHashMappings() {
        final Long2ObjectOpenHashMap<String> source = new Long2ObjectOpenHashMap<>();
        source.put(0L, "zero");
        source.put(23L, "twenty-three");

        final Long2ObjectOpenHashMap<String> restored = SerializationUtils.clone(source);

        assertThat(restored.get(0L)).isEqualTo("zero");
        assertThat(restored.get(23L)).isEqualTo("twenty-three");
    }
}
