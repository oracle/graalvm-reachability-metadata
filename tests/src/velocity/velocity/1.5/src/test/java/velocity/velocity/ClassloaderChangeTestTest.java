/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Base64;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Introspector;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassloaderChangeTestTest {
    private static final byte[] FOO_CLASS_BYTES = Base64.getDecoder().decode("""
            yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAOSGVs
            bG8gRnJvbSBGb28HAAoBAANGb28BAARDb2RlAQAPTGluZU51bWJlclRhYmxlAQAEZG9JdAEAFCgpTGphdmEvbGFu
            Zy9TdHJpbmc7AQAKU291cmNlRmlsZQEACEZvby5qYXZhACEACQACAAAAAAACAAEABQAGAAEACwAAAB0AAQABAAAA
            BSq3AAGxAAAAAQAMAAAABgABAAAAAQABAA0ADgABAAsAAAAbAAEAAQAAAAMSB7AAAAABAAwAAAAGAAEAAAADAAEA
            DwAAAAIAEA==
            """.replaceAll("\\s", ""));

    @Test
    public void clearsIntrospectionCacheAfterClassLoaderChange() throws Exception {
        try {
            final Introspector introspector = new Introspector(new Log(new NullLogChute()));

            assertThat(invokeDoIt(introspector, loadFooClass())).isEqualTo("Hello From Foo");
            introspector.triggerClear();
            assertThat(invokeDoIt(introspector, loadFooClass())).isEqualTo("Hello From Foo");
        } catch (ClassNotFoundException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)
                    && !isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        }
    }

    private static Class<?> loadFooClass() throws ClassNotFoundException {
        return new ByteArrayClassLoader().loadClass("Foo");
    }

    private static Object invokeDoIt(final Introspector introspector, final Class<?> fooClass) throws Exception {
        final Method method = introspector.getMethod(fooClass, "doIt", new Object[0]);
        final Object instance = fooClass.getDeclaredConstructor().newInstance();
        return method.invoke(instance);
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(final Throwable throwable) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = throwable;
        while (current != null) {
            if ((current instanceof ClassNotFoundException || current instanceof NoClassDefFoundError)
                    && "Foo".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static final class ByteArrayClassLoader extends ClassLoader {
        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            if ("Foo".equals(name)) {
                return defineClass(name, FOO_CLASS_BYTES, 0, FOO_CLASS_BYTES.length);
            }
            throw new ClassNotFoundException(name);
        }
    }
}
