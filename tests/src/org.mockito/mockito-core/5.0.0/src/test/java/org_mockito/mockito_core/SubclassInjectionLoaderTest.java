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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class SubclassInjectionLoaderTest {
    private static final String MOCK_MAKER_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MockMaker";
    private static final String MOCK_RESOLVER_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MockResolver";
    private static final String MEMBER_ACCESSOR_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MemberAccessor";
    private static final String NO_UNSAFE_INJECTION_PROPERTY =
            "org.mockito.internal.noUnsafeInjection";
    private static final String TEST_CLASS_NAME = SubclassInjectionLoaderTest.class.getName();

    @Test
    void subclassMockMakerCreatesStubbedConcreteClassWithoutUnsafeInjection() throws Exception {
        try {
            String greeting = mockWithFreshLookupBasedSubclassMockMaker();

            assertThat(greeting).isEqualTo("Hello Mockito");
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

    private static String mockWithFreshLookupBasedSubclassMockMaker() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        String originalNoUnsafeInjection = System.getProperty(NO_UNSAFE_INJECTION_PROPERTY);
        URL[] classPathUrls = currentClassPathUrls();

        try (LookupSubclassMockMakerClassLoader classLoader =
                new LookupSubclassMockMakerClassLoader(classPathUrls)) {
            System.setProperty(NO_UNSAFE_INJECTION_PROPERTY, "true");
            Thread.currentThread().setContextClassLoader(classLoader);

            Class<?> accessClass =
                    Class.forName(IsolatedSubclassMockAccess.class.getName(), true, classLoader);
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
            if (originalNoUnsafeInjection == null) {
                System.clearProperty(NO_UNSAFE_INJECTION_PROPERTY);
            } else {
                System.setProperty(NO_UNSAFE_INJECTION_PROPERTY, originalNoUnsafeInjection);
            }
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

    private static final class LookupSubclassMockMakerClassLoader extends URLClassLoader {
        private LookupSubclassMockMakerClassLoader(URL[] urls) {
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

    public static final class IsolatedSubclassMockAccess implements Callable<String> {
        @Override
        public String call() {
            System.setProperty("net.bytebuddy.experimental", "true");
            System.setProperty(NO_UNSAFE_INJECTION_PROPERTY, "true");
            GreetingService service =
                    Mockito.mock(
                            GreetingService.class,
                            withSettings().mockMaker(MockMakers.SUBCLASS));

            when(service.greeting("Mockito")).thenReturn("Hello Mockito");

            if (!MockMakers.SUBCLASS.equals(
                    Mockito.mockingDetails(service).getMockCreationSettings().getMockMaker())) {
                throw new AssertionError("Expected the subclass mock maker to create the mock");
            }
            return service.greeting("Mockito");
        }
    }

    public static class GreetingService {
        public String greeting(String name) {
            return "Hello " + name;
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
