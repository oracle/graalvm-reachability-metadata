/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jersey.core.util.KeyComparator;
import com.sun.jersey.core.util.KeyComparatorHashMap;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class KeyComparatorHashMapTest {
    @Test
    void serializesAndDeserializesEntriesThroughCustomMapFormat() throws Exception {
        final KeyComparatorHashMap<String, String> map = new KeyComparatorHashMap<String, String>(
                new CaseInsensitiveKeyComparator());
        map.put("Content-Type", "text/plain");

        final byte[] serialized = serialize(map);
        final KeyComparatorHashMap<String, String> deserialized = deserialize(serialized);

        assertThat(deserialized).hasSize(1);
        assertThat(deserialized.get("CONTENT-TYPE")).isEqualTo("text/plain");
        assertThat(deserialized.containsKey("content-type")).isTrue();
    }

    private static byte[] serialize(KeyComparatorHashMap<String, String> map) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static KeyComparatorHashMap<String, String> deserialize(byte[] serialized) throws Exception {
        final ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStream input = new ObjectInputStream(bytes)) {
            return (KeyComparatorHashMap<String, String>) input.readObject();
        }
    }

    private static final class CaseInsensitiveKeyComparator implements KeyComparator<String>, Serializable {
        private static final long serialVersionUID = 1L;

        @Override
        public int hash(String key) {
            return key.toLowerCase(Locale.ROOT).hashCode();
        }

        @Override
        public boolean equals(String first, String second) {
            return first.equalsIgnoreCase(second);
        }

        @Override
        public int compare(String first, String second) {
            return String.CASE_INSENSITIVE_ORDER.compare(first, second);
        }
    }
}
