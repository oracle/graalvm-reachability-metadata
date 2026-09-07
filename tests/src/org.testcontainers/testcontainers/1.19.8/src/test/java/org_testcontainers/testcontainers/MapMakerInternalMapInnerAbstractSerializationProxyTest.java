/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.collect.MapMaker;
import org.testcontainers.shaded.org.apache.commons.lang3.SerializationUtils;

import java.io.Serializable;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void serializesEntriesThroughTheMapProxy() {
        ConcurrentMap<String, String> values = new MapMaker().weakKeys().makeMap();
        values.put("key", "value");

        ConcurrentMap<?, ?> restored = (ConcurrentMap<?, ?>) SerializationUtils.roundtrip((Serializable) values);

        assertThat(restored).containsEntry("key", "value");
    }
}
