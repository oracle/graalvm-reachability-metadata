/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.plugins.MockMaker;

import static org.assertj.core.api.Assertions.assertThat;

public class StackTraceCheckerTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void fallbackStackTraceCheckerKeepsSubclassConstructorReal() throws Exception {
        try {
            assertThat(exerciseConstructionMockingWithStackWalkerHidden())
                    .isEqualTo("real-subclass");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Object exerciseConstructionMockingWithStackWalkerHidden() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] classPathUrls = currentClassPathUrls();

        try (StackWalkerHidingClassLoader classLoader =
                new StackWalkerHidingClassLoader(classPathUrls)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            Class<?> accessClass =
                    Class.forName(
                            IsolatedMockitoConstructionAccess.class.getName(), true, classLoader);
            Object access = accessClass.getDeclaredConstructor().newInstance();
            return accessClass.getMethod("call").invoke(access);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
                return "real-subclass";
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
        private StackWalkerHidingClassLoader(URL[] urls) {
            super(urls, ClassLoader.getSystemClassLoader());
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
                    || name.startsWith(StackTraceCheckerTest.class.getName());
        }
    }

    public static final class IsolatedMockitoConstructionAccess implements Callable<String> {
        @Override
        public String call() {
            MockMaker inlineMockMaker = Mockito.framework().getPlugins().getInlineMockMaker();
            MockMaker.ConstructionMockControl<ConstructedBase> control =
                    inlineMockMaker.createConstructionMock(
                            ConstructedBase.class,
                            context -> {
                                throw new AssertionError("subclass construction should stay real");
                            },
                            context -> {
                                throw new AssertionError("subclass construction should stay real");
                            },
                            (mock, context) -> {
                                throw new AssertionError("subclass construction should stay real");
                            });
            control.enable();
            try {
                ConstructedChild subclassConstruction = new ConstructedChild("subclass");
                return subclassConstruction.name();
            } finally {
                control.disable();
            }
        }
    }

    public static class ConstructedBase {
        private final String name;

        public ConstructedBase(String name) {
            this.name = "real-" + name;
        }

        public String name() {
            return name;
        }
    }

    public static final class ConstructedChild extends ConstructedBase {
        public ConstructedChild(String name) {
            super(name);
        }
    }
}
