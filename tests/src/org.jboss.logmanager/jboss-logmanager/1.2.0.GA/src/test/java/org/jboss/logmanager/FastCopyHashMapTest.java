/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jboss.logmanager;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.jupiter.api.Test;

class FastCopyHashMapTest {

    @Test
    void serializesAndDeserializesEntriesIncludingNullKeysAndValues() throws Exception {
        final FastCopyHashMap<String, String> map = new FastCopyHashMap<>();
        map.put(null, "root");
        map.put("alpha", "one");
        map.put("beta", null);

        final FastCopyHashMap<String, String> restored = roundTrip(map);

        assertThat(restored)
                .hasSize(3)
                .containsEntry(null, "root")
                .containsEntry("alpha", "one")
                .containsEntry("beta", null);

        restored.put("gamma", "three");

        assertThat(restored).containsEntry("gamma", "three");
    }

    @SuppressWarnings("unchecked")
    private static <T> T roundTrip(final T value) throws IOException, ClassNotFoundException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(value);
        }

        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(outputStream.toByteArray()))) {
            return (T) objectInputStream.readObject();
        }
    }
}
