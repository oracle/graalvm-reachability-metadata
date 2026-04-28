/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import io.netty.util.internal.chmv8.ConcurrentHashMapV8;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConcurrentHashMapV8Test {
    @Test
    void serializationRoundTripPreservesEntries() throws Exception {
        ConcurrentHashMapV8<String, String> original = new ConcurrentHashMapV8<String, String>();
        original.put("first", "alpha");
        original.put("second", "bravo");
        original.put("third", "charlie");

        ConcurrentHashMapV8<String, String> restored = deserialize(serialize(original));

        Assertions.assertEquals(original.size(), restored.size());
        Assertions.assertEquals("alpha", restored.get("first"));
        Assertions.assertEquals("bravo", restored.get("second"));
        Assertions.assertEquals("charlie", restored.get("third"));
    }

    private static byte[] serialize(ConcurrentHashMapV8<String, String> map) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(map);
        }
        return bytes.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentHashMapV8<String, String> deserialize(byte[] bytes)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (ConcurrentHashMapV8<String, String>) in.readObject();
        }
    }
}
