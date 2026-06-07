/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_atomikos.atomikos_util;

import static org.assertj.core.api.Assertions.assertThat;

import com.atomikos.util.SerializableObjectFactory;
import java.util.Base64;
import java.util.Hashtable;
import javax.naming.BinaryRefAddr;
import javax.naming.Reference;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class SerializableObjectFactoryAnonymous1Test {
    private static final String CONTEXT_ONLY_CLASS_NAME =
            "com_atomikos.atomikos_util.context.ContextOnlySerializablePayload";
    private static final String CONTEXT_ONLY_CLASS_BYTES = """
            yv66vgAAADQAJgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW\
            CQAIAAkHAAoMAAsADAEAQWNvbV9hdG9taWtvcy9hdG9taWtvc191dGlsL2NvbnRleHQvQ29udGV4\
            dE9ubHlTZXJpYWxpemFibGVQYXlsb2FkAQAFdmFsdWUBABJMamF2YS9sYW5nL1N0cmluZzsHAA4B\
            ABdqYXZhL2xhbmcvU3RyaW5nQnVpbGRlcgoADQADCAARAQANY29udGV4dC1vbmx5OgoADQATDAAU\
            ABUBAAZhcHBlbmQBAC0oTGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvU3RyaW5nQnVpbGRl\
            cjsKAA0AFwwAGAAZAQAIdG9TdHJpbmcBABQoKUxqYXZhL2xhbmcvU3RyaW5nOwcAGwEAFGphdmEv\
            aW8vU2VyaWFsaXphYmxlAQAQc2VyaWFsVmVyc2lvblVJRAEAAUoBAA1Db25zdGFudFZhbHVlBQAA\
            AAAAAAABAQAVKExqYXZhL2xhbmcvU3RyaW5nOylWAQAEQ29kZQEAD0xpbmVOdW1iZXJUYWJsZQEA\
            ClNvdXJjZUZpbGUBACNDb250ZXh0T25seVNlcmlhbGl6YWJsZVBheWxvYWQuamF2YQAxAAgAAgAB\
            ABoAAgAaABwAHQABAB4AAAACAB8AEgALAAwAAAACAAEABQAhAAEAIgAAACoAAgACAAAACiq3AAEq\
            K7UAB7EAAAABACMAAAAOAAMAAAAKAAQACwAJAAwAAQAYABkAAQAiAAAALwACAAEAAAAXuwANWbcA\
            DxIQtgASKrQAB7YAErYAFrAAAAABACMAAAAGAAEAAAAQAAEAJAAAAAIAJQ==\
            """;
    private static final String SERIALIZED_CONTEXT_ONLY_OBJECT = """
            rO0ABXNyAEFjb21fYXRvbWlrb3MuYXRvbWlrb3NfdXRpbC5jb250ZXh0LkNvbnRleHRPbmx5U2Vy\
            aWFsaXphYmxlUGF5bG9hZAAAAAAAAAABAgABTAAFdmFsdWV0ABJMamF2YS9sYW5nL1N0cmluZzt4\
            cHQACGF0b21pa29z\
            """;

    @Test
    void resolvesSerializedClassWithThreadContextClassLoaderWhenDefaultResolutionCannotFindIt() throws Exception {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader contextOnlyClassLoader = new ContextOnlyClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextOnlyClassLoader);

            final Reference reference = new Reference(
                    CONTEXT_ONLY_CLASS_NAME,
                    new BinaryRefAddr("com.atomikos.serializable", decode(SERIALIZED_CONTEXT_ONLY_OBJECT)),
                    SerializableObjectFactory.class.getName(),
                    null
            );
            final Object restored = new SerializableObjectFactory()
                    .getObjectInstance(reference, null, null, new Hashtable<>());

            assertThat(restored.getClass().getName()).isEqualTo(CONTEXT_ONLY_CLASS_NAME);
            assertThat(restored).hasToString("context-only:atomikos");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static byte[] decode(String bytes) {
        return Base64.getDecoder().decode(bytes);
    }

    private static final class ContextOnlyClassLoader extends ClassLoader {
        ContextOnlyClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!CONTEXT_ONLY_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            final byte[] bytes = decode(CONTEXT_ONLY_CLASS_BYTES);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
