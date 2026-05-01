/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import autovalue.shaded.com.google$.common.collect.$MapMaker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AutovalueShadedComGoogleInnerCommonCollectInnerMapMakerInternalMapInnerAbstractSerializationProxyTest {
    @Test
    void weakValueMapSerializationProxyPreservesEntriesAcrossRoundTrip() throws Exception {
        ConcurrentMap<String, TimeUnit> original = new $MapMaker()
                .initialCapacity(8)
                .concurrencyLevel(2)
                .weakValues()
                .makeMap();
        original.put("processor", TimeUnit.MILLISECONDS);
        original.put("compiler", TimeUnit.SECONDS);
        original.put("native-image", TimeUnit.MINUTES);

        ConcurrentMap<String, TimeUnit> restored = roundTrip(original);

        assertThat(restored).isNotSameAs(original);
        assertThat(restored).hasSize(original.size());
        assertThat(snapshot(restored)).containsExactlyInAnyOrderEntriesOf(snapshot(original));
        assertThat(restored.get("processor")).isSameAs(TimeUnit.MILLISECONDS);
        assertThat(restored.get("compiler")).isSameAs(TimeUnit.SECONDS);
        assertThat(restored.get("native-image")).isSameAs(TimeUnit.MINUTES);
    }

    private static ConcurrentMap<String, TimeUnit> roundTrip(
            ConcurrentMap<String, TimeUnit> value
    ) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object restored = objectInputStream.readObject();
            assertThat(restored).isInstanceOf(ConcurrentMap.class);
            @SuppressWarnings("unchecked")
            ConcurrentMap<String, TimeUnit> typedRestored = (ConcurrentMap<String, TimeUnit>) restored;
            return typedRestored;
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }
        return outputStream.toByteArray();
    }

    private static Map<String, TimeUnit> snapshot(ConcurrentMap<String, TimeUnit> value) {
        return new LinkedHashMap<>(value);
    }
}
