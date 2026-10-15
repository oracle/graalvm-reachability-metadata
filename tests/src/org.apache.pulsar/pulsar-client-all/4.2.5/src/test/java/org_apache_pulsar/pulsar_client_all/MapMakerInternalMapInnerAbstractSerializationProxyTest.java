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
import org.junit.jupiter.api.Timeout;

@Timeout(55)
public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void preservesWeakValueMapEntriesThroughSerializationProxy() {
        ConcurrentMap<String, String> original = new MapMaker().weakValues().makeMap();
        original.put("topic", "persistent://public/default/orders");

        Object restored = SerializationUtils.roundtrip((Serializable) original);

        assertThat(restored).isInstanceOf(Map.class);
        Map<?, ?> restoredMap = (Map<?, ?>) restored;
        assertThat(restoredMap.get("topic"))
                .isEqualTo("persistent://public/default/orders");
    }
}
