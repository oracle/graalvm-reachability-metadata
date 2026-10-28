/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.pulsar.shade.com.google.common.collect.MapMaker;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void roundTripPreservesWeakValueMapConfigurationAndEntries() throws Exception {
        ConcurrentMap<String, TimeUnit> input = new MapMaker().weakValues().concurrencyLevel(2).makeMap();
        input.put("short", TimeUnit.SECONDS);
        input.put("long", TimeUnit.DAYS);

        ConcurrentMap<String, TimeUnit> restored = SerializationSupport.roundTrip(input);

        assertThat(restored).containsEntry("short", TimeUnit.SECONDS).containsEntry("long", TimeUnit.DAYS);
        assertThat(restored.getClass().getName()).contains("MapMakerInternalMap");
    }
}
