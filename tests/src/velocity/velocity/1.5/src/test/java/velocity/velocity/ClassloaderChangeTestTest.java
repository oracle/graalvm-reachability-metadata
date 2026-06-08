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
    public void clearsIntrospectionCacheForClassNameLoadedAgain() throws Exception {
        try {
            final Introspector introspector = new Introspector(new Log(new NullLogChute()));
            final Class<?> firstFooClass = new FooClassLoader().defineFooClass();
            final Method firstMethod = introspector.getMethod(firstFooClass, "doIt", new Object[0]);
            assertThat(firstMethod.invoke(firstFooClass.getDeclaredConstructor().newInstance()))
                    .isEqualTo("Hello From Foo");

            final Class<?> secondFooClass = new FooClassLoader().defineFooClass();
            final Method secondMethod = introspector.getMethod(
                    secondFooClass, "doIt", new Object[0]);

            assertThat(secondMethod).isNotNull();
            assertThat(secondMethod.getDeclaringClass()).isSameAs(secondFooClass);
            assertThat(secondMethod.invoke(secondFooClass.getDeclaredConstructor().newInstance()))
                    .isEqualTo("Hello From Foo");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class FooClassLoader extends ClassLoader {
        private Class<?> defineFooClass() {
            return defineClass("Foo", FOO_CLASS_BYTES, 0, FOO_CLASS_BYTES.length);
        }
    }
}
