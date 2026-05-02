/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

import org.junit.jupiter.api.Test;

public class EnumMapTest {
    @Test
    void readObjectConsumesSerializedMappings() throws Exception {
        java.util.EnumMap<TestKey, String> originalMap = new java.util.EnumMap<>(TestKey.class);
        originalMap.put(TestKey.FIRST, "first-value");

        Object deserializedMap = readWithGwtEnumMapDescriptor(originalMap);

        assertThat(deserializedMap.getClass().getName())
                .isEqualTo("com.google.gwt.user.server.rpc.EnumMap");
    }

    private static Object readWithGwtEnumMapDescriptor(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }

        ByteArrayInputStream input = new ByteArrayInputStream(bytes.toByteArray());
        try (ObjectInputStream objectInput = new GwtEnumMapInputStream(input)) {
            return objectInput.readObject();
        }
    }

    private static final class GwtEnumMapInputStream extends ObjectInputStream {
        private GwtEnumMapInputStream(InputStream input) throws IOException {
            super(input);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass descriptor)
                throws IOException, ClassNotFoundException {
            if ("java.util.EnumMap".equals(descriptor.getName())) {
                ClassLoader testClassLoader = EnumMapTest.class.getClassLoader();
                return Class.forName("com.google.gwt.user.server.rpc.EnumMap", false,
                        testClassLoader);
            }
            return super.resolveClass(descriptor);
        }
    }

    private enum TestKey {
        FIRST
    }
}
