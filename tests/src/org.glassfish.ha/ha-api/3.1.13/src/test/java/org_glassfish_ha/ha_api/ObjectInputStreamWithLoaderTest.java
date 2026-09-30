/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_ha.ha_api;

import org.glassfish.ha.store.spi.ObjectInputStreamWithLoader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class ObjectInputStreamWithLoaderTest {
    @Test
    void readsAnObjectUsingTheConfiguredLoader() throws Exception {
        String value = readWithLoader("hello", String.class, ClassLoader.getSystemClassLoader());

        assertEquals("hello", value);
    }

    @Test
    void readsAnApplicationObjectUsingTheConfiguredLoader() throws Exception {
        Payload original = new Payload("session", 42);
        RecordingClassLoader loader = new RecordingClassLoader();

        Payload restored = readWithLoader(original, Payload.class, loader);

        assertEquals(Payload.class.getName(), loader.requestedClassName());
        assertEquals(original.name(), restored.name());
        assertEquals(original.revision(), restored.revision());
    }

    @Test
    void readsObjectArraysUsingTheConfiguredLoader() throws Exception {
        String[] values = readWithLoader(new String[]{"one", "two"}, String[].class,
                new RejectingClassLoader());

        assertArrayEquals(new String[]{"one", "two"}, values);
    }

    @Test
    void readsPrimitiveArrays() throws Exception {
        int[] values = readWithLoader(new int[]{1, 2, 3}, int[].class,
                ClassLoader.getSystemClassLoader());

        assertArrayEquals(new int[]{1, 2, 3}, values);
    }

    @Test
    void fallsBackToTheDefaultLoaderWhenTheConfiguredLoaderCannotLoadAClass() throws Exception {
        String value = readWithLoader("fallback", String.class, new RejectingClassLoader());

        assertEquals("fallback", value);
    }

    private static <T> T readWithLoader(T value, Class<T> type, ClassLoader loader)
            throws IOException, ClassNotFoundException {
        byte[] serialized = serialize(value);
        try (ObjectInputStreamWithLoader input = new ObjectInputStreamWithLoader(
                new ByteArrayInputStream(serialized), loader)) {
            return assertInstanceOf(type, input.readObject());
        }
    }

    private static byte[] serialize(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private record Payload(String name, int revision) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}

final class RecordingClassLoader extends ClassLoader {
    private String requestedClassName;

    RecordingClassLoader() {
        super(ObjectInputStreamWithLoaderTest.class.getClassLoader());
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        requestedClassName = name;
        return super.loadClass(name);
    }

    String requestedClassName() {
        return requestedClassName;
    }
}

final class RejectingClassLoader extends ClassLoader {
    RejectingClassLoader() {
        super(ClassLoader.getSystemClassLoader());
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        if (String.class.getName().equals(name)) {
            throw new ClassNotFoundException(name);
        }
        return super.loadClass(name);
    }
}
