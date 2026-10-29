/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.alibaba.nacos.shaded.com.google.common.collect.MapMaker;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void restoresWeakValueConcurrentMapsThroughTheirSerializationProxy() throws Exception {
        ConcurrentMap<String, String> map = new MapMaker().weakValues().makeMap();
        String value = new String("application");
        map.put("config", value);
        ConcurrentMap<String, String> restored = TestSupport.roundTrip(map);
        assertEquals(value, restored.get("config"));
        assertEquals("application", restored.putIfAbsent("config", "replacement"));
        assertEquals(1, restored.size());
    }
}
