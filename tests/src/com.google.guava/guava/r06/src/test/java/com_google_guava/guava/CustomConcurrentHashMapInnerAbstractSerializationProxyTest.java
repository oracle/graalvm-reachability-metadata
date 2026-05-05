/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import com.google.common.collect.MapMaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CustomConcurrentHashMapInnerAbstractSerializationProxyTest {
    @Test
    void serializesAndDeserializesMapEntriesThroughSerializationProxy() throws Exception {
        ConcurrentMap<String, Integer> original = new MapMaker()
                .expiration(1, TimeUnit.DAYS)
                .makeMap();
        original.put("one", 1);
        original.put("two", 2);

        ConcurrentMap<String, Integer> copy = roundTrip(original);

        assertThat(copy).containsEntry("one", 1)
                .containsEntry("two", 2)
                .hasSize(2);
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        ByteArrayInputStream inputBytes = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream input = new ObjectInputStream(inputBytes)) {
            return (T) input.readObject();
        }
    }
}
