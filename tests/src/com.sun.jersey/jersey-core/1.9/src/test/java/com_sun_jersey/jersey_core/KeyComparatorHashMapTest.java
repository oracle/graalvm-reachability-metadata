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
import java.io.Serializable;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class KeyComparatorHashMapTest {
    @Test
    void serializationPreservesEntriesAndKeyComparator() throws Exception {
        KeyComparatorHashMap<String, String> headers = new KeyComparatorHashMap<>(
                CaseInsensitiveKeyComparator.INSTANCE);
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "text/plain");

        KeyComparatorHashMap<String, String> deserialized = deserialize(serialize(headers));

        assertThat(deserialized).isNotSameAs(headers);
        assertThat(deserialized).hasSize(2);
        assertThat(deserialized.get("ACCEPT")).isEqualTo("application/json");
        assertThat(deserialized.get("content-type")).isEqualTo("text/plain");
    }

    private static byte[] serialize(KeyComparatorHashMap<String, String> headers) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(headers);
        }
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static KeyComparatorHashMap<String, String> deserialize(byte[] data)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInput = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (KeyComparatorHashMap<String, String>) objectInput.readObject();
        }
    }

    private enum CaseInsensitiveKeyComparator implements KeyComparator<String>, Serializable {
        INSTANCE;

        @Override
        public int compare(String first, String second) {
            return first.compareToIgnoreCase(second);
        }

        @Override
        public boolean equals(String first, String second) {
            return first.equalsIgnoreCase(second);
        }

        @Override
        public int hash(String value) {
            return value.toLowerCase(Locale.ROOT).hashCode();
        }
    }
}
