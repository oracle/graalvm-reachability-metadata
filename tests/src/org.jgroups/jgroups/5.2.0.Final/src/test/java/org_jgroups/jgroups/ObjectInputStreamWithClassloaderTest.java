/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.util.ObjectInputStreamWithClassloader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class ObjectInputStreamWithClassloaderTest {
    @BeforeAll
    static void configureLoopbackDefaults() {
        System.setProperty("jgroups.bind_addr", "127.0.0.1");
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("jgroups.use.jdk_logger", "true");
    }

    @Test
    void fallsBackToDefaultObjectStreamResolutionWhenProvidedLoaderCannotLoadClass() throws Exception {
        ArrayList<String> original = new ArrayList<>();
        original.add("alpha");
        original.add("beta");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(original);
        }

        try (ObjectInputStreamWithClassloader input = new ObjectInputStreamWithClassloader(
                new ByteArrayInputStream(bytes.toByteArray()), new RejectingArrayListClassLoader())) {
            Object restored = input.readObject();

            assertThat(restored).isInstanceOf(ArrayList.class);
            assertThat(restored).isEqualTo(original);
        }
    }

    private static final class RejectingArrayListClassLoader extends ClassLoader {
        RejectingArrayListClassLoader() {
            super(ObjectInputStreamWithClassloaderTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (ArrayList.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
