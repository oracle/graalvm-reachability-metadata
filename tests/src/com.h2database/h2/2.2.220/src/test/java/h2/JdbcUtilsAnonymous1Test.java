/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.api.JavaObjectSerializer;
import org.h2.util.JdbcUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;

public class JdbcUtilsAnonymous1Test {
    @Test
    void fallsBackToObjectInputStreamResolutionWhenThreadContextLoaderCannotLoadClass() throws Exception {
        JavaObjectSerializer configuredSerializer = JdbcUtils.serializer;
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        BlockingClassLoader blockingClassLoader = new BlockingClassLoader(originalClassLoader,
                ArrayList.class.getName());
        JdbcUtils.serializer = null;
        Thread.currentThread().setContextClassLoader(blockingClassLoader);
        try {
            ArrayList<String> values = new ArrayList<>();
            values.add("first");
            values.add("second");

            Object deserialized = JdbcUtils.deserialize(serialize(values), null);

            assertThat(blockingClassLoader.wasBlocked()).isTrue();
            assertThat(deserialized).isInstanceOf(ArrayList.class);
            assertThat(deserialized).isEqualTo(values);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            JdbcUtils.serializer = configuredSerializer;
        }
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static class BlockingClassLoader extends ClassLoader {
        private final String blockedClassName;
        private boolean blocked;

        BlockingClassLoader(ClassLoader parent, String blockedClassName) {
            super(parent);
            this.blockedClassName = blockedClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (blockedClassName.equals(name)) {
                blocked = true;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        boolean wasBlocked() {
            return blocked;
        }
    }
}
