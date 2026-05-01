/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_reload4j.reload4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import org.apache.log4j.helpers.Loader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class LoaderTest {
    private static final String CONTEXT_RESOURCE = "reload4j-loader-context-resource.txt";
    private static final String MISSING_RESOURCE = "missing/reload4j-loader-resource.txt";

    @Test
    void findsResourcesWithThreadContextClassLoader() throws Exception {
        URL expectedUrl = new URL("file:/reload4j-loader-context-resource.txt");
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ResourceClassLoader(CONTEXT_RESOURCE, expectedUrl));
        try {
            URL resourceUrl = Loader.getResource(CONTEXT_RESOURCE);

            assertThat(resourceUrl).isNotNull();
            assertThat(resourceUrl.toExternalForm()).isEqualTo(expectedUrl.toExternalForm());
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToLoaderClassLoaderAndSystemResources() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new EmptyResourceClassLoader());
        try {
            URL resourceUrl = Loader.getResource(MISSING_RESOURCE);

            assertThat(resourceUrl).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToSystemResourcesWhenContextResourceLookupFails() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new ThrowingResourceClassLoader());
        try {
            URL resourceUrl = Loader.getResource(MISSING_RESOURCE);

            assertThat(resourceUrl).isNull();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void loadsClassesWithThreadContextClassLoader() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread()
                .setContextClassLoader(new SelectiveLoadClassLoader(Integer.class.getName(), Integer.class));
        try {
            Class<?> loadedClass = Loader.loadClass(Integer.class.getName());

            assertThat(loadedClass).isSameAs(Integer.class);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderCannotLoadClass() throws Exception {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(new RejectingLoadClassLoader(String.class.getName()));
        try {
            Class<?> loadedClass = Loader.loadClass(String.class.getName());

            assertThat(loadedClass).isSameAs(String.class);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void honorsIgnoreThreadContextClassLoaderPropertyWhenLoadedFresh() throws Exception {
        try {
            CodeSource codeSource = Loader.class.getProtectionDomain().getCodeSource();
            assertThat(codeSource).isNotNull();
            URL reload4jUrl = codeSource.getLocation();
            String previousIgnoreTcl = System.getProperty("log4j.ignoreTCL");
            System.setProperty("log4j.ignoreTCL", "true");
            try (URLClassLoader reload4jClassLoader = new URLClassLoader(new URL[] { reload4jUrl }, null)) {
                Class<?> isolatedLoaderClass = Class.forName("org.apache.log4j.helpers.Loader", true,
                        reload4jClassLoader);
                Object loadedClass = isolatedLoaderClass.getMethod("loadClass", String.class).invoke(null,
                        String.class.getName());

                assertThat(loadedClass).isSameAs(String.class);
            } finally {
                restoreSystemProperty("log4j.ignoreTCL", previousIgnoreTcl);
            }
        } catch (InvocationTargetException exception) {
            Throwable targetException = exception.getTargetException();
            if (targetException instanceof Error error) {
                rethrowIfNotNativeImageDynamicClassLoadingError(error);
            }
            throw exception;
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static void restoreSystemProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class ResourceClassLoader extends ClassLoader {
        private final String resourceName;
        private final URL resourceUrl;

        private ResourceClassLoader(String resourceName, URL resourceUrl) {
            super(null);
            this.resourceName = resourceName;
            this.resourceUrl = resourceUrl;
        }

        @Override
        public URL getResource(String name) {
            if (resourceName.equals(name)) {
                return resourceUrl;
            }
            return null;
        }
    }

    private static final class EmptyResourceClassLoader extends ClassLoader {
        private EmptyResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }

    private static final class ThrowingResourceClassLoader extends ClassLoader {
        private ThrowingResourceClassLoader() {
            super(null);
        }

        @Override
        public URL getResource(String name) {
            throw new IllegalStateException("resource lookup failed");
        }
    }

    private static final class SelectiveLoadClassLoader extends ClassLoader {
        private final String className;
        private final Class<?> loadedClass;

        private SelectiveLoadClassLoader(String className, Class<?> loadedClass) {
            super(null);
            this.className = className;
            this.loadedClass = loadedClass;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (className.equals(name)) {
                return loadedClass;
            }
            return super.loadClass(name);
        }
    }

    private static final class RejectingLoadClassLoader extends ClassLoader {
        private final String rejectedClassName;

        private RejectingLoadClassLoader(String rejectedClassName) {
            super(null);
            this.rejectedClassName = rejectedClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (rejectedClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
