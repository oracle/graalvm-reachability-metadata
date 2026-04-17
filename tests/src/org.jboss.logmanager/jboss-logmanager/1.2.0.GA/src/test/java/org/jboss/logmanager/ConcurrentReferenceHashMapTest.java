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
    void preservesEntriesAcrossJavaSerialization() throws Exception {
        ConcurrentReferenceHashMap<String, String> original = new ConcurrentReferenceHashMap<>(
                4,
                ConcurrentReferenceHashMap.ReferenceType.STRONG,
                ConcurrentReferenceHashMap.ReferenceType.STRONG
        );
        original.put("alpha", "one");
        original.put("beta", "two");

        ConcurrentReferenceHashMap<String, String> restored = roundTrip(original);

        assertThat(restored)
                .isNotSameAs(original)
                .hasSize(2)
                .containsEntry("alpha", "one")
                .containsEntry("beta", "two");
    }

    private static ConcurrentReferenceHashMap<String, String> roundTrip(
            ConcurrentReferenceHashMap<String, String> map) throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(map);
        Object restored = deserialize(serialized);

        assertThat(restored).isInstanceOf(ConcurrentReferenceHashMap.class);

        @SuppressWarnings("unchecked")
        ConcurrentReferenceHashMap<String, String> typedRestored = (ConcurrentReferenceHashMap<String, String>) restored;
        return typedRestored;
    }

    private static byte[] serialize(ConcurrentReferenceHashMap<String, String> map) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
            objectOutputStream.writeObject(map);
        }
        return outputStream.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return objectInputStream.readObject();
        }
    }
}
