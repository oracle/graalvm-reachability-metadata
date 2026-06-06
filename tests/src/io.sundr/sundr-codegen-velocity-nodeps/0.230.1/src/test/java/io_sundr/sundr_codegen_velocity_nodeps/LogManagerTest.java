/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.sundr.deps.org.apache.velocity.exception.VelocityException;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeConstants;
import io.sundr.deps.org.apache.velocity.runtime.RuntimeInstance;
import io.sundr.deps.org.apache.velocity.runtime.log.LogManager;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;
import java.util.concurrent.Callable;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class LogManagerTest {
    private static final String LOG_CHUTE_CLASS_NAME =
            "io.sundr.deps.org.apache.velocity.runtime.log.LogChute";

    @Test
    void runtimeInitializationRejectsLoggerClassThatDoesNotImplementLogChute() {
        final RuntimeInstance runtime = new RuntimeInstance();
        runtime.setProperty(
                RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                NonLogChute.class.getName());

        final Throwable thrown = catchThrowable(runtime::init);

        assertThat(thrown)
                .isInstanceOf(VelocityException.class)
                .hasMessageContaining(NonLogChute.class.getName());
        final Throwable wrapped = ((VelocityException) thrown).getWrappedThrowable();
        assertThat(wrapped).isInstanceOf(VelocityException.class);
        assertThat(wrapped.getMessage())
                .contains("does not implement")
                .contains(LOG_CHUTE_CLASS_NAME);
    }

    @Test
    void isolatedLoggerValidationResolvesLogChuteInterfaceName() throws Exception {
        try (ChildFirstClassLoader classLoader = new ChildFirstClassLoader(new URL[] {
                codeSourceUrl(LogManagerTest.class),
                codeSourceUrl(LogManager.class)
        }, LogManagerTest.class.getClassLoader())) {
            final Class<?> actionClass = Class.forName(
                    LogManagerTest.class.getName() + "$IsolatedAction",
                    true,
                    classLoader);
            final Callable<?> action = actionClass.asSubclass(Callable.class)
                    .getDeclaredConstructor()
                    .newInstance();

            final String result = (String) action.call();

            assertThat(result)
                    .contains("Failed to initialize an instance of ")
                    .contains(NonLogChute.class.getName())
                    .contains("does not implement")
                    .contains(LOG_CHUTE_CLASS_NAME);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class IsolatedAction implements Callable<String> {
        @Override
        public String call() {
            final Thread thread = Thread.currentThread();
            final ClassLoader originalContextClassLoader = thread.getContextClassLoader();
            thread.setContextClassLoader(getClass().getClassLoader());
            try {
                final RuntimeInstance runtime = new RuntimeInstance();
                runtime.setProperty(
                        RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
                        NonLogChute.class.getName());

                try {
                    LogManager.updateLog(runtime.getLog(), runtime);
                    return "LogManager accepted invalid log chute";
                } catch (VelocityException exception) {
                    final Throwable wrapped = exception.getWrappedThrowable();
                    return exception.getMessage() + "\n"
                            + (wrapped == null ? "" : wrapped.getMessage());
                } catch (Exception exception) {
                    return exception.getMessage();
                }
            } finally {
                thread.setContextClassLoader(originalContextClassLoader);
            }
        }
    }

    public static class NonLogChute {
        public NonLogChute() {
        }
    }

    private static URL codeSourceUrl(Class<?> type) {
        final CodeSource codeSource = type.getProtectionDomain().getCodeSource();

        assertThat(codeSource).isNotNull();
        return codeSource.getLocation();
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
            return name.equals(IsolatedAction.class.getName())
                    || name.equals(NonLogChute.class.getName())
                    || name.startsWith("io.sundr.deps.");
        }
    }
}
