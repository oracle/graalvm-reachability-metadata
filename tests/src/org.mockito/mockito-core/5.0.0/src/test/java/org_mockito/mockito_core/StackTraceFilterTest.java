/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.Location;

import static org.assertj.core.api.Assertions.assertThat;

public class StackTraceFilterTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void filtersFirstMockitoLocationWhenStackWalkerIsUnavailable() throws Exception {
        try {
            String location = (String) exerciseMockitoInvocationLocationWithStackWalkerHidden();

            assertThat(location)
                    .contains("StackTraceFilterTest.java")
                    .contains("IsolatedMockitoInvocationAccess.call");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Object exerciseMockitoInvocationLocationWithStackWalkerHidden()
            throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] classPathUrls = currentClassPathUrls();

        try (StackWalkerHidingClassLoader classLoader =
                new StackWalkerHidingClassLoader(classPathUrls)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            Class<?> accessClass =
                    Class.forName(
                            IsolatedMockitoInvocationAccess.class.getName(), true, classLoader);
            Callable<?> access = (Callable<?>) accessClass.getDeclaredConstructor().newInstance();
            return access.call();
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static URL[] currentClassPathUrls() throws Exception {
        String[] classPathEntries =
                System.getProperty("java.class.path", "").split(File.pathSeparator);
        List<URL> urls = new ArrayList<>();
        for (String classPathEntry : classPathEntries) {
            if (!classPathEntry.isBlank()) {
                urls.add(Path.of(classPathEntry).toUri().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static final class StackWalkerHidingClassLoader extends URLClassLoader {
        private static final String MOCK_RESOLVER_EXTENSION =
                "mockito-extensions/org.mockito.plugins.MockResolver";

        private StackWalkerHidingClassLoader(URL[] urls) {
            super(urls, ClassLoader.getSystemClassLoader());
        }

        @Override
        public URL getResource(String name) {
            if (MOCK_RESOLVER_EXTENSION.equals(name)) {
                return null;
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (MOCK_RESOLVER_EXTENSION.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("java.lang.StackWalker") || name.startsWith("java.lang.StackWalker$")) {
                throw new ClassNotFoundException(name);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && isIsolatedClass(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = super.loadClass(name, false);
                    }
                } else if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static boolean isIsolatedClass(String name) {
            return name.startsWith("org.mockito.")
                    || name.startsWith(StackTraceFilterTest.class.getName());
        }
    }

    public static final class IsolatedMockitoInvocationAccess implements Callable<String> {
        @Override
        public String call() {
            LocationService service =
                    Mockito.mock(
                            LocationService.class,
                            Mockito.withSettings().mockMaker(MockMakers.SUBCLASS));

            service.call("value");

            Collection<Invocation> invocations = Mockito.mockingDetails(service).getInvocations();
            Location location = invocations.iterator().next().getLocation();
            return location + "\n" + location.getSourceFile();
        }
    }

    public interface LocationService {
        void call(String value);
    }
}
