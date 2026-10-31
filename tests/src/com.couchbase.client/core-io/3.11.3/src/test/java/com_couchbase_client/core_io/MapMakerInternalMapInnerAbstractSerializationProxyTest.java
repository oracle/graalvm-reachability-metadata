/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.com.google.common.collect.MapMaker;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void preservesContents() throws Exception {
        ConcurrentMap<String, String> original = new MapMaker().weakValues().makeMap();
        String value = new String("cached-value");
        original.put("entry", value);
        ConcurrentMap<String, String> restored = SerializationSupport.roundTrip(original);
        assertEquals(value, restored.get("entry"));
        assertEquals(value, original.get("entry"));
        assertEquals(1, restored.size());
    }
}
