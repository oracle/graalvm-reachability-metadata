/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.ObjectInputStreamWithClassloader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectInputStreamWithClassloaderTest {
    private static final String PAYLOAD_CLASS_NAME = ArrayList.class.getName();

    @Test
    void deserializesClassesThroughProvidedClassLoader() throws Exception {
        RecordingClassLoader loader = new RecordingClassLoader(List.of());
        byte[] serialized = serialize(new ArrayList<>(List.of("alpha", "bravo")));

        Object result = deserialize(serialized, loader);

        assertThat(result).isEqualTo(List.of("alpha", "bravo"));
        assertThat(loader.requestedClassNames()).contains(PAYLOAD_CLASS_NAME);
    }

    @Test
    void fallsBackToDefaultClassResolutionWhenProvidedClassLoaderCannotLoadClass() throws Exception {
        RecordingClassLoader loader = new RecordingClassLoader(List.of(PAYLOAD_CLASS_NAME));
        byte[] serialized = serialize(new ArrayList<>(List.of("charlie", "delta")));

        Object result = deserialize(serialized, loader);

        assertThat(result).isEqualTo(List.of("charlie", "delta"));
        assertThat(loader.requestedClassNames()).contains(PAYLOAD_CLASS_NAME);
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] serialized, ClassLoader loader) throws Exception {
        ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
        try (ObjectInputStreamWithClassloader input = new ObjectInputStreamWithClassloader(bytes, loader)) {
            return input.readObject();
        }
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> hiddenClassNames;
        private final List<String> requestedClassNames = new ArrayList<>();

        private RecordingClassLoader(List<String> hiddenClassNames) {
            super(RecordingClassLoader.class.getClassLoader());
            this.hiddenClassNames = hiddenClassNames;
        }

        private List<String> requestedClassNames() {
            return requestedClassNames;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            requestedClassNames.add(name);
            if (hiddenClassNames.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
