/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.MapMaker;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class MapMakerInnerStrategyImplTest {
    @Test
    void customMapSerializationPreservesEntries() throws Exception {
        ConcurrentMap<String, String> map = new MapMaker()
                .expiration(1, TimeUnit.DAYS)
                .makeMap();
        map.put("first", "one");
        map.put("second", "two");

        ConcurrentMap<String, String> restored = roundTrip(map);

        assertThat(restored)
                .containsEntry("first", "one")
                .containsEntry("second", "two")
                .hasSize(2);
        restored.put("third", "three");
        assertThat(restored).containsEntry("third", "three");
    }

    @SuppressWarnings("unchecked")
    private static <K, V> ConcurrentMap<K, V> roundTrip(ConcurrentMap<K, V> map)
            throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }

        try (ObjectInputStream input = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            return (ConcurrentMap<K, V>) input.readObject();
        }
    }
}
