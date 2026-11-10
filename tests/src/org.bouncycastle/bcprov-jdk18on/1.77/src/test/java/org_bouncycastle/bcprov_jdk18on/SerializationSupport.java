/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.Key;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SerializationSupport {
    private SerializationSupport() {
    }

    @SuppressWarnings("unchecked")
    static <T extends Serializable> T roundTrip(T value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (T) input.readObject();
        }
    }

    static Key persistKey(Key original) throws IOException, ClassNotFoundException {
        byte[] encoding = original.getEncoded();
        assertTrue(encoding.length > 0);
        Key restored = roundTrip(original);
        assertNotSame(original, restored);
        assertEquals(original.getClass(), restored.getClass());
        assertEquals(original.getAlgorithm(), restored.getAlgorithm());
        assertEquals(original.getFormat(), restored.getFormat());
        assertArrayEquals(encoding, restored.getEncoded());
        return restored;
    }
}
