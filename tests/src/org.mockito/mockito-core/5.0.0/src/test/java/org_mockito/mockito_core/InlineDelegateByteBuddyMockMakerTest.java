/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;

import static org.assertj.core.api.Assertions.assertThat;

public class InlineDelegateByteBuddyMockMakerTest {
    @Test
    void inlineMockMakerReportsInitializationFailureWhenDispatcherResourceCannotBeRead()
            throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader previousContextClassLoader = currentThread.getContextClassLoader();
        try (ChildFirstLibraryClassLoader classLoader =
                new ChildFirstLibraryClassLoader(testClasspathUrls(), true)) {
            currentThread.setContextClassLoader(classLoader);
            Class<?> mockito = classLoader.loadClass("org.mockito.Mockito");

            try {
                createInlineMock(mockito, Runnable.class);
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof Error error
                        && NativeImageSupport.isUnsupportedFeatureError(error)) {
                    return;
                }
                assertThat(cause)
                        .isInstanceOf(RuntimeException.class)
                        .hasStackTraceContaining(
                                "Could not initialize inline Byte Buddy mock maker");
                return;
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        } finally {
            currentThread.setContextClassLoader(previousContextClassLoader);
        }
        throw new AssertionError("Expected inline mock maker initialization to fail");
    }

    private static Object createInlineMock(Class<?> mockito, Class<?> typeToMock) throws Exception {
        Class<?> mockSettings = mockito.getClassLoader().loadClass("org.mockito.MockSettings");
        Object settings = mockito.getMethod("withSettings").invoke(null);
        Method mockMaker = mockSettings.getMethod("mockMaker", String.class);
        settings = mockMaker.invoke(settings, MockMakers.INLINE);
        Method mock = mockito.getMethod("mock", Class.class, mockSettings);
        return mock.invoke(null, typeToMock, settings);
    }

    private static URL[] testClasspathUrls() {
        return Arrays.stream(System.getProperty("java.class.path", "").split(File.pathSeparator))
                .filter(entry -> !entry.isEmpty())
                .map(File::new)
                .map(File::toURI)
                .map(uri -> {
                    try {
                        return uri.toURL();
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .toArray(URL[]::new);
    }

    private static class ChildFirstLibraryClassLoader extends URLClassLoader {
        private static final String MOCK_METHOD_DISPATCHER_RESOURCE =
                "org/mockito/internal/creation/bytebuddy/inject/MockMethodDispatcher.raw";

        private final boolean hideMockMethodDispatcherResource;

        ChildFirstLibraryClassLoader(URL[] urls) {
            this(urls, false);
        }

        ChildFirstLibraryClassLoader(URL[] urls, boolean hideMockMethodDispatcherResource) {
            super(urls, ClassLoader.getPlatformClassLoader());
            this.hideMockMethodDispatcherResource = hideMockMethodDispatcherResource;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (hideMockMethodDispatcherResource
                    && MOCK_METHOD_DISPATCHER_RESOURCE.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isLibraryClass(name)) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            loaded = findClass(name);
                        } catch (ClassNotFoundException ignored) {
                            loaded = super.loadClass(name, false);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private static boolean isLibraryClass(String name) {
            return name.startsWith("org.mockito.")
                    || name.startsWith("net.bytebuddy.")
                    || name.startsWith("org.objenesis.");
        }
    }
}
