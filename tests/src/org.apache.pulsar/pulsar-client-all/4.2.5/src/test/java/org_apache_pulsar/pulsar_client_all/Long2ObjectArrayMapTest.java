/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pulsar.shade.it.unimi.dsi.fastutil.longs.Long2ObjectArrayMap;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class Long2ObjectArrayMapTest {

    @Test
    void preservesPrimitiveLongMappings() {
        final Long2ObjectArrayMap<String> source = new Long2ObjectArrayMap<>();
        source.put(1L, "one");
        source.put(2L, "two");

        final Long2ObjectArrayMap<String> restored = SerializationUtils.clone(source);

        assertThat(restored.get(1L)).isEqualTo("one");
        assertThat(restored.get(2L)).isEqualTo("two");
    }
}
