/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.apache.pulsar.shade.com.google.common.collect.MapMaker;
import org.apache.pulsar.shade.org.apache.commons.lang3.SerializationUtils;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {

    @Test
    void preservesEntriesInAWeakKeyConcurrentMap() {
        final ConcurrentMap<Partition, Integer> source = new MapMaker().weakKeys().makeMap();
        source.put(Partition.ZERO, 0);
        source.put(Partition.ONE, 1);

        final Map<?, ?> restored = (Map<?, ?>) SerializationUtils.clone((Serializable) source);

        assertThat(restored.get(Partition.ZERO)).isEqualTo(0);
        assertThat(restored.get(Partition.ONE)).isEqualTo(1);
    }

    private enum Partition {
        ZERO,
        ONE
    }
}
