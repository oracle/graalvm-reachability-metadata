/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class Base64Anonymous1Test {
    private static final String BASE64_CLASS_NAME = "io.undertow.websockets.core.protocol.version07.Base64";
    private static final int NO_OPTIONS = 0;

    @Test
    void decodeToObjectUsesProvidedClassLoaderToResolveSerializedClasses() throws Exception {
        EncodedPayload message = new EncodedPayload("undertow websocket base64 deserialization");
        String encoded = encodeObject(message);
        DelegatingClassLoader loader = new DelegatingClassLoader(Base64Anonymous1Test.class.getClassLoader());

        Object decoded = decodeToObject(encoded, loader);

        assertThat(decoded).isEqualTo(message);
        assertThat(loader.loadedEncodedPayload()).isTrue();
    }

    @Test
    void decodeToObjectHandlesNonResolvingClassLoaderWithoutHanging() throws Exception {
        EncodedPayload message = new EncodedPayload("undertow websocket base64 fallback deserialization");
        String encoded = encodeObject(message);
        ClassLoader loader = new NonResolvingClassLoader();

        try {
            assertThat(decodeToObject(encoded, loader)).isEqualTo(message);
        } catch (ClassNotFoundException | NullPointerException expected) {
            assertThat(expected).isNotNull();
        }
    }

    private static String encodeObject(Serializable value) throws Exception {
        Method encodeObject = base64Class().getDeclaredMethod("encodeObject", Serializable.class);
        encodeObject.setAccessible(true);
        return invokeStatic(encodeObject, String.class, value);
    }

    private static Object decodeToObject(String encoded, ClassLoader loader) throws Exception {
        Method decodeToObject = base64Class()
                .getDeclaredMethod("decodeToObject", String.class, int.class, ClassLoader.class);
        decodeToObject.setAccessible(true);
        return invokeStatic(decodeToObject, Object.class, encoded, NO_OPTIONS, loader);
    }

    private static Class<?> base64Class() throws ClassNotFoundException {
        return Class.forName(BASE64_CLASS_NAME);
    }

    private static <T> T invokeStatic(Method method, Class<T> resultType, Object... arguments) throws Exception {
        try {
            return resultType.cast(method.invoke(null, arguments));
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checkedException) {
                throw checkedException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw exception;
        }
    }

    private record EncodedPayload(String value) implements Serializable {
    }

    private static final class DelegatingClassLoader extends ClassLoader {
        private final ClassLoader delegate;
        private boolean loadedEncodedPayload;

        private DelegatingClassLoader(ClassLoader delegate) {
            super(null);
            this.delegate = Objects.requireNonNull(delegate);
        }

        private boolean loadedEncodedPayload() {
            return loadedEncodedPayload;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (EncodedPayload.class.getName().equals(name)) {
                loadedEncodedPayload = true;
            }
            Class<?> loadedClass = delegate.loadClass(name);
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }
    }

    private static final class NonResolvingClassLoader extends ClassLoader {
        private NonResolvingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) {
            return null;
        }
    }
}
