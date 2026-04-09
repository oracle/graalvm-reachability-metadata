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

class ConcurrentReferenceHashMapTest {

    @Test
    void serializesAndDeserializesStoredEntries() throws Exception {
        final ConcurrentReferenceHashMap<String, String> map = new ConcurrentReferenceHashMap<>(
                4,
                ConcurrentReferenceHashMap.ReferenceType.STRONG,
                ConcurrentReferenceHashMap.ReferenceType.STRONG);
        map.put("alpha", "one");
        map.put("beta", "two");

        final ConcurrentReferenceHashMap<String, String> restored = roundTrip(map);

        assertThat(restored)
                .hasSize(2)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two");

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
