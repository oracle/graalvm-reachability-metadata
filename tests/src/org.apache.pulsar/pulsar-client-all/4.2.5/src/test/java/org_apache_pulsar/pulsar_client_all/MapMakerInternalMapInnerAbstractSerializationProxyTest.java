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
        final ConcurrentMap<String, Integer> source = new MapMaker().weakKeys().makeMap();
        source.put("partition-zero", 0);
        source.put("partition-one", 1);

        final Map<?, ?> restored = (Map<?, ?>) SerializationUtils.clone((Serializable) source);

        assertThat(restored.get("partition-zero")).isEqualTo(0);
        assertThat(restored.get("partition-one")).isEqualTo(1);
    }
}
