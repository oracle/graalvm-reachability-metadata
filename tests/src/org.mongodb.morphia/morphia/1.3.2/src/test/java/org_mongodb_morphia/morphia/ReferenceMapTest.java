/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb_morphia.morphia;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import relocated.morphia.org.apache.commons.collections.ReferenceMap;

import static org.assertj.core.api.Assertions.assertThat;

public class ReferenceMapTest {
    @Test
    void serializationHooksWriteAndReadEntries() throws Throwable {
        ReferenceMap original = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD, 4, 0.75f);
        original.put("first", "Ada");
        original.put("second", "Lovelace");

        byte[] serialized = writeReferenceMapBody(original);
        ReferenceMap restored = readReferenceMapBody(serialized);

        assertThat(restored).hasSize(2)
                .containsEntry("first", "Ada")
                .containsEntry("second", "Lovelace");
    }

    private static byte[] writeReferenceMapBody(final ReferenceMap map) throws Throwable {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ReferenceMapObjectOutputStream output = new ReferenceMapObjectOutputStream(bytes)) {
            writeObjectHandle().invoke(map, output);
        }
        return bytes.toByteArray();
    }

    private static ReferenceMap readReferenceMapBody(final byte[] bytes) throws Throwable {
        ReferenceMap map = new ReferenceMap(ReferenceMap.HARD, ReferenceMap.HARD, 4, 0.75f);
        try (ReferenceMapObjectInputStream input = new ReferenceMapObjectInputStream(new ByteArrayInputStream(bytes))) {
            readObjectHandle().invoke(map, input);
        }
        return map;
    }

    private static MethodHandle writeObjectHandle() throws ReflectiveOperationException {
        return referenceMapLookup().findVirtual(ReferenceMap.class, "writeObject",
                MethodType.methodType(void.class, ObjectOutputStream.class));
    }

    private static MethodHandle readObjectHandle() throws ReflectiveOperationException {
        return referenceMapLookup().findVirtual(ReferenceMap.class, "readObject",
                MethodType.methodType(void.class, ObjectInputStream.class));
    }

    private static MethodHandles.Lookup referenceMapLookup() throws IllegalAccessException {
        return MethodHandles.privateLookupIn(ReferenceMap.class, MethodHandles.lookup());
    }

    private static final class ReferenceMapObjectOutputStream extends ObjectOutputStream {
        private ReferenceMapObjectOutputStream(final ByteArrayOutputStream output) throws IOException {
            super(output);
        }

        @Override
        public void defaultWriteObject() {
            // ReferenceMap declares serialization hooks but does not implement Serializable.
        }
    }

    private static final class ReferenceMapObjectInputStream extends ObjectInputStream {
        private ReferenceMapObjectInputStream(final ByteArrayInputStream input) throws IOException {
            super(input);
        }

        @Override
        public void defaultReadObject() {
            // ReferenceMap declares serialization hooks but does not implement Serializable.
        }
    }
}
