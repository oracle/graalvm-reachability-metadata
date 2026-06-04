/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.MockMakers;
import org.mockito.Mockito;
import org.mockito.mock.SerializableMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.withSettings;

public class ByteBuddyCrossClassLoaderSerializationSupportInnerCrossClassLoaderSerializationProxyTest {
    private static final String MOCK_MAKER_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MockMaker";
    private static final String MOCK_RESOLVER_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MockResolver";
    private static final String MEMBER_ACCESSOR_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MemberAccessor";
    private static final String TEST_CLASS_NAME =
            ByteBuddyCrossClassLoaderSerializationSupportInnerCrossClassLoaderSerializationProxyTest
                    .class
                    .getName();

    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void acrossClassLoaderSerializableMockRoundTripsThroughJavaSerialization() throws Exception {
        try {
            assertThat(roundTripWithByteBuddyMockMaker()).isEqualTo("hello from mock");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (Exception exception) {
            Error unsupportedFeatureError = findUnsupportedFeatureError(exception);
            if (unsupportedFeatureError == null) {
                throw exception;
            }
        }
    }

    private static String roundTripWithByteBuddyMockMaker() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] classPathUrls = currentClassPathUrls();

        try (ByteBuddyMockMakerClassLoader classLoader =
                new ByteBuddyMockMakerClassLoader(classPathUrls)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            Class<?> accessClass =
                    Class.forName(IsolatedSerializationAccess.class.getName(), true, classLoader);
            Object access = accessClass.getDeclaredConstructor().newInstance();
            return (String) accessClass.getMethod("call").invoke(access);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw exception;
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static URL[] currentClassPathUrls() throws Exception {
        String[] classPathEntries = System.getProperty("java.class.path", "")
                .split(File.pathSeparator);
        List<URL> urls = new ArrayList<>();
        for (String classPathEntry : classPathEntries) {
            if (!classPathEntry.isBlank()) {
                urls.add(Path.of(classPathEntry).toUri().toURL());
            }
        }
        return urls.toArray(URL[]::new);
    }

    private static Error findUnsupportedFeatureError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return (Error) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private static final class ByteBuddyMockMakerClassLoader extends URLClassLoader {
        private ByteBuddyMockMakerClassLoader(URL[] urls) {
            super(urls, ClassLoader.getSystemClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
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
            return name.startsWith("org.mockito.") || name.startsWith(TEST_CLASS_NAME);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (MOCK_RESOLVER_RESOURCE.equals(name) || MEMBER_ACCESSOR_RESOURCE.equals(name)) {
                return Collections.emptyEnumeration();
            }

            List<URL> resources = new ArrayList<>();
            if (MOCK_MAKER_RESOURCE.equals(name)) {
                resources.add(inMemoryPluginResource(name, MockMakers.SUBCLASS));
            }
            resources.addAll(Collections.list(super.getResources(name)));
            return Collections.enumeration(resources);
        }
    }

    public static final class IsolatedSerializationAccess implements Callable<String> {
        @Override
        public String call() throws Exception {
            SerializableGreetingService mock =
                    Mockito.mock(
                            SerializableGreetingService.class,
                            withSettings()
                                    .mockMaker(MockMakers.SUBCLASS)
                                    .serializable(SerializableMode.ACROSS_CLASSLOADERS));
            Mockito.when(mock.greeting()).thenReturn("hello from mock");

            SerializableGreetingService deserialized = roundTrip(mock);

            if (!Mockito.mockingDetails(deserialized).isMock()) {
                throw new AssertionError("Deserialized object should be a Mockito mock");
            }
            return deserialized.greeting();
        }

        private static SerializableGreetingService roundTrip(SerializableGreetingService mock)
                throws Exception {
            byte[] serialized;
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                    ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
                outputStream.writeObject(mock);
                outputStream.flush();
                serialized = bytes.toByteArray();
            }

            try (ObjectInputStream inputStream =
                    new ObjectInputStream(new ByteArrayInputStream(serialized))) {
                return (SerializableGreetingService) inputStream.readObject();
            }
        }
    }

    public static class SerializableGreetingService implements Serializable {
        private static final long serialVersionUID = 1L;

        public String greeting() {
            return "real greeting";
        }
    }

    private static URL inMemoryPluginResource(String resourceName, String content)
            throws IOException {
        return new URL(
                null,
                "memory:///" + resourceName,
                new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            public void connect() {
                            }

                            @Override
                            public InputStream getInputStream() {
                                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                                return new ByteArrayInputStream(bytes);
                            }
                        };
                    }
                });
    }
}
