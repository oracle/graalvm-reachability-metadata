/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import com.sun.jersey.core.util.KeyComparator;
import com.sun.jersey.core.util.KeyComparatorHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KeyComparatorHashMapTest {
    @Test
    public void serializesAndDeserializesEntriesWithKeyComparator() throws IOException, ClassNotFoundException {
        final KeyComparatorHashMap<String, String> map =
                new KeyComparatorHashMap<>(CaseInsensitiveKeyComparator.INSTANCE);
        map.put("Content-Type", "application/json");
        map.put("Accept", "text/plain");

        final byte[] serializedMap = serialize(map);

        final KeyComparatorHashMap<String, String> restoredMap = deserialize(serializedMap);
        assertThat(restoredMap).hasSize(2);
        assertThat(restoredMap.get("content-type")).isEqualTo("application/json");
        assertThat(restoredMap.get("ACCEPT")).isEqualTo("text/plain");
    }

    private static byte[] serialize(KeyComparatorHashMap<String, String> map) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static KeyComparatorHashMap<String, String> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (KeyComparatorHashMap<String, String>) input.readObject();
        }
    }

    private enum CaseInsensitiveKeyComparator implements KeyComparator<String> {
        INSTANCE;

        @Override
        public int hash(String key) {
            return key.toLowerCase().hashCode();
        }

        @Override
        public boolean equals(String firstKey, String secondKey) {
            return firstKey.equalsIgnoreCase(secondKey);
        }

        @Override
        public int compare(String firstKey, String secondKey) {
            return firstKey.compareToIgnoreCase(secondKey);
        }
    }
}
