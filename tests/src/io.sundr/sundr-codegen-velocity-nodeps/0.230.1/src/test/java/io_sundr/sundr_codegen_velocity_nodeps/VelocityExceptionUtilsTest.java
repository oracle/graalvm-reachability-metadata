/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.util.ExceptionUtils;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.concurrent.Callable;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class VelocityExceptionUtilsTest {
    private static final IllegalArgumentException STATIC_RUNTIME_CAUSE =
            new IllegalArgumentException("runtime cause");
    private static final RuntimeException STATIC_RUNTIME_EXCEPTION =
            ExceptionUtils.createRuntimeException("runtime wrapper", STATIC_RUNTIME_CAUSE);

    @Test
    public void isolatedLoaderResolvesLegacyClassLiteral() throws Exception {
        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(VelocityExceptionUtilsTest.class),
                codeSourceUrl(ExceptionUtils.class)
        }, VelocityExceptionUtilsTest.class.getClassLoader())) {
            Class<?> actionClass = Class.forName(
                    VelocityExceptionUtilsTest.class.getName() + "$IsolatedAction",
                    true,
                    classLoader);
            Class<?> isolatedExceptionUtilsClass = Class.forName(
                    ExceptionUtils.class.getName(),
                    true,
                    classLoader);

            if (isNativeImageRuntime() && isolatedExceptionUtilsClass == ExceptionUtils.class) {
                return;
            }

            Callable<?> action = actionClass.asSubclass(Callable.class)
                    .getDeclaredConstructor()
                    .newInstance();

            assertThat(action.call()).isEqualTo("isolated wrapper:isolated cause");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    public void createAndInitializeExceptionsWithCauses() {
        assertThat(STATIC_RUNTIME_EXCEPTION)
                .hasMessage("runtime wrapper")
                .hasCause(STATIC_RUNTIME_CAUSE);

        IllegalStateException initializedCause = new IllegalStateException("initialized cause");
        Exception initializedException = new Exception("initialized wrapper");
        ExceptionUtils.setCause(initializedException, initializedCause);

        assertThat(initializedException).hasCause(initializedCause);

        IllegalArgumentException fallbackCause = new IllegalArgumentException("fallback cause");
        Throwable fallbackException = ExceptionUtils.createWithCause(
                StringOnlyException.class,
                "fallback wrapper",
                fallbackCause);

        assertThat(fallbackException)
                .isInstanceOf(StringOnlyException.class)
                .hasMessageContaining("fallback wrapper")
                .hasMessageContaining("fallback cause");
    }

    public static class StringOnlyException extends Exception {
        public StringOnlyException(String message) {
            super(message);
        }
    }

    public static class IsolatedAction implements Callable<String> {
        @Override
        public String call() {
            IllegalArgumentException cause = new IllegalArgumentException("isolated cause");
            RuntimeException exception = ExceptionUtils.createRuntimeException(
                    "isolated wrapper",
                    cause);
            return exception.getMessage() + ":" + exception.getCause().getMessage();
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class ChildFirstClassLoader extends URLClassLoader {
        private final ClassLoader parent;

        private ChildFirstClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
            this.parent = parent;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && shouldLoadChildFirst(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = parent.loadClass(name);
                    }
                }
                if (loadedClass == null) {
                    loadedClass = parent.loadClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static boolean shouldLoadChildFirst(String name) {
            String isolatedActionName = VelocityExceptionUtilsTest.class.getName()
                    + "$IsolatedAction";
            return name.equals(ExceptionUtils.class.getName())
                    || name.startsWith(isolatedActionName);
        }
    }
}
