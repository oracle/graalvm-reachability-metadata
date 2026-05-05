/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye.jandex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

public class StrongInternPoolTest {
    /**
     * Serialized `StrongInternPool` containing the string `jandex-entry`.
     */
    private static final String SERIALIZED_POOL_WITH_ENTRY = """
            rO0ABXNyACFvcmcuamJvc3MuamFuZGV4LlN0cm9uZ0ludGVyblBvb2wAAAnwvRNwOgMAAUYACmxvYWRGYWN0
            b3J4cD8rhR93BAAAAAF0AAxqYW5kZXgtZW50cnl4
            """;

    @Test
    void serializesPoolWithEntries() throws Exception {
        Object pool = readSerializedPool(Base64.getMimeDecoder().decode(SERIALIZED_POOL_WITH_ENTRY));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(pool);
        }

        byte[] serialized = bytes.toByteArray();
        Object roundTrippedPool = readSerializedPool(serialized);

        assertEquals("org.jboss.jandex.StrongInternPool", roundTrippedPool.getClass().getName());
        assertTrue(new String(serialized, StandardCharsets.ISO_8859_1).contains("jandex-entry"));
    }

    private static Object readSerializedPool(byte[] serialized) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            Object pool = input.readObject();
            assertEquals("org.jboss.jandex.StrongInternPool", pool.getClass().getName());
            return pool;
        }
    }
}
