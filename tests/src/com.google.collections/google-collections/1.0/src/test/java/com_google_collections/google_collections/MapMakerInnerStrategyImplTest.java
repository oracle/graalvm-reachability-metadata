/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_collections.google_collections;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.MapMaker;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

public class MapMakerInnerStrategyImplTest {
    @Test
    void serializedCustomMapPreservesEntriesAndMutability() throws Exception {
        ConcurrentMap<String, String> original = new MapMaker().softValues().makeMap();
        original.put("first", "one");
        original.put("second", "two");

        ConcurrentMap<String, String> restored = deserialize(serialize((Serializable) original));

        assertThat(restored.get("first")).isEqualTo("one");
        assertThat(restored.get("second")).isEqualTo("two");
        assertThat(restored.putIfAbsent("third", "three")).isNull();
        assertThat(restored.get("third")).isEqualTo("three");
    }

    private static byte[] serialize(Serializable value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static <T> T deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (T) input.readObject();
        }
    }
}
