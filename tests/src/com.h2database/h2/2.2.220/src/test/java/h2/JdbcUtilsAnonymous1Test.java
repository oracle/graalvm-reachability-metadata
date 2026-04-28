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
    void resolvesClassWithThreadContextLoader() throws Exception {
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(
                Thread.currentThread().getContextClassLoader(), ArrayList.class.getName(), false);
        ArrayList<String> values = values();

        Object deserialized = deserializeWithContextLoader(values, trackingClassLoader);

        assertThat(trackingClassLoader.wasRequested()).isTrue();
        assertThat(deserialized).isInstanceOf(ArrayList.class);
        assertThat(deserialized).isEqualTo(values);
    }

    @Test
    void fallsBackToObjectInputStreamResolutionWhenThreadContextLoaderCannotLoadClass() throws Exception {
        TrackingClassLoader trackingClassLoader = new TrackingClassLoader(
                Thread.currentThread().getContextClassLoader(), ArrayList.class.getName(), true);
        ArrayList<String> values = values();

        Object deserialized = deserializeWithContextLoader(values, trackingClassLoader);

        assertThat(trackingClassLoader.wasRequested()).isTrue();
        assertThat(deserialized).isInstanceOf(ArrayList.class);
        assertThat(deserialized).isEqualTo(values);
    }

    private static Object deserializeWithContextLoader(Object value, ClassLoader classLoader) throws Exception {
        JavaObjectSerializer configuredSerializer = JdbcUtils.serializer;
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        JdbcUtils.serializer = null;
        Thread.currentThread().setContextClassLoader(classLoader);
        try {
            return JdbcUtils.deserialize(serialize(value), null);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
            JdbcUtils.serializer = configuredSerializer;
        }
    }

    private static ArrayList<String> values() {
        ArrayList<String> values = new ArrayList<>();
        values.add("first");
        values.add("second");
        return values;
    }

    private static byte[] serialize(Object value) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutput = new ObjectOutputStream(output)) {
            objectOutput.writeObject(value);
        }
        return output.toByteArray();
    }

    private static class TrackingClassLoader extends ClassLoader {
        private final String trackedClassName;
        private final boolean failTrackedClass;
        private boolean requested;

        TrackingClassLoader(ClassLoader parent, String trackedClassName, boolean failTrackedClass) {
            super(parent);
            this.trackedClassName = trackedClassName;
            this.failTrackedClass = failTrackedClass;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (trackedClassName.equals(name)) {
                requested = true;
                if (failTrackedClass) {
                    throw new ClassNotFoundException(name);
                }
            }
            return super.loadClass(name, resolve);
        }

        boolean wasRequested() {
            return requested;
        }
    }
}
