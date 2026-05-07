/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;

import org.apache.pulsar.client.internal.ReflectionUtilsAccess;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

public class ReflectionUtilsTest {
    private static final String CONTEXT_ONLY_CLASS_NAME =
            "org_apache_pulsar.pulsar_client_api.context.ContextOnly";
    private static final String CONTEXT_ONLY_CLASS_BYTES = """
            yv66vgAAADQADQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW\
            BwAIAQA3b3JnX2FwYWNoZV9wdWxzYXIvcHVsc2FyX2NsaWVudF9hcGkvY29udGV4dC9Db250ZXh0\
            T25seQEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAApTb3VyY2VGaWxlAQAQQ29udGV4dE9ubHku\
            amF2YQAhAAcAAgAAAAAAAQABAAUABgABAAkAAAAdAAEAAQAAAAUqtwABsQAAAAEACgAAAAYAAQ\
            AAAAMAAQALAAAAAgAM\
            """;

    @Test
    void loadsClassWithDefaultImplementationClassLoader() {
        final String className = ReflectionUtilsAccess.loadClassName(ReflectionUtilsFixture.class.getName());

        assertThat(className).isEqualTo(ReflectionUtilsFixture.class.getName());
    }

    @Test
    void loadsClassWithThreadContextClassLoaderWhenDefaultLoaderCannotFindIt() {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        final ClassLoader contextOnlyClassLoader = new ContextOnlyClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(contextOnlyClassLoader);

            final String className = ReflectionUtilsAccess.loadClassName(CONTEXT_ONLY_CLASS_NAME);

            assertThat(className).isEqualTo(CONTEXT_ONLY_CLASS_NAME);
        } catch (RuntimeException exception) {
            if (isUnsupportedNativeImageContextOnlyClassLoading(exception)) {
                throw new TestAbortedException(
                        "Native image runtime does not support loading ad hoc classes via the thread context ClassLoader",
                        exception
                );
            }
            throw exception;
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void findsPublicConstructor() {
        final String declaringClassName = ReflectionUtilsAccess.constructorDeclaringClassName(
                ReflectionUtilsFixture.class.getName(), String.class);

        assertThat(declaringClassName).isEqualTo(ReflectionUtilsFixture.class.getName());
    }

    @Test
    void findsPublicStaticMethod() {
        final String returnTypeName = ReflectionUtilsAccess.staticMethodReturnTypeName(
                ReflectionUtilsFixture.class.getName(), "describe", Integer.class);

        assertThat(returnTypeName).isEqualTo(String.class.getName());
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static boolean isUnsupportedNativeImageContextOnlyClassLoading(RuntimeException exception) {
        if (!"runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"))) {
            return false;
        }

        Throwable current = exception;
        while (current != null) {
            if (current instanceof ClassNotFoundException
                    && CONTEXT_ONLY_CLASS_NAME.equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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
            final byte[] bytes = Base64.getDecoder().decode(CONTEXT_ONLY_CLASS_BYTES);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
