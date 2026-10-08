/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.MapMaker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class MapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void weakValueMapRoundTripSerializesEntriesThroughMapMakerProxy() throws Exception {
        ConcurrentMap<String, TimeUnit> map = new MapMaker().weakValues().concurrencyLevel(2).makeMap();
        map.put("short", TimeUnit.SECONDS);
        map.put("long", TimeUnit.DAYS);

        ConcurrentMap<String, TimeUnit> restored = roundTripConcurrentMap(map);

        assertThat(restored).hasSize(2);
        assertThat(restored.get("short")).isSameAs(TimeUnit.SECONDS);
        assertThat(restored.get("long")).isSameAs(TimeUnit.DAYS);
        assertThat(restored.getClass().getName()).contains("MapMakerInternalMap");
    }

    private static ConcurrentMap<String, TimeUnit> roundTripConcurrentMap(
            ConcurrentMap<String, TimeUnit> map) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(map);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream inputStream = new ObjectInputStream(inputBytes)) {
            Object restored = inputStream.readObject();
            assertThat(restored).isInstanceOf(ConcurrentMap.class);
            @SuppressWarnings("unchecked")
            ConcurrentMap<String, TimeUnit> typedRestored = (ConcurrentMap<String, TimeUnit>) restored;
            return typedRestored;
        }
    }
}
