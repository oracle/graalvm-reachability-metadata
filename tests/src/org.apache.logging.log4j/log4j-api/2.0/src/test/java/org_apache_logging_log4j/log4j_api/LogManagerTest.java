/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.opentest4j.TestAbortedException;

public class LogManagerTest {
    private static final String FACTORY_PROPERTY_NAME = "log4j2.loggerContextFactory";

    @Test
    void initializesFactoryFromConfiguredClassAndProviderResource() throws Exception {
        assertIsolatedLogManagerUsesFactory(
            FailingLoggerContextFactory.class.getName(),
            ProviderLoggerContextFactory.class.getName());
    }

    @Test
    void initializesIsolatedLogManagerFromConfiguredFactory() throws Exception {
        assertIsolatedLogManagerUsesFactory(
            SuccessfulConfiguredLoggerContextFactory.class.getName(),
            SuccessfulConfiguredLoggerContextFactory.class.getName());
    }

    private static void assertIsolatedLogManagerUsesFactory(final String configuredFactoryClassName,
                                                           final String expectedFactoryClassName) throws Exception {
        String previousFactory = System.getProperty(FACTORY_PROPERTY_NAME);
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (ChildFirstUrlClassLoader loader = new ChildFirstUrlClassLoader(classPathUrls())) {
            System.setProperty(FACTORY_PROPERTY_NAME, configuredFactoryClassName);
            Thread.currentThread().setContextClassLoader(loader);
            assertIsolatedFactoryInitialized(loader, expectedFactoryClassName);
        } catch (InvocationTargetException exception) {
            if (isUnsupportedDynamicClassLoading(exception.getCause())) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (!isUnsupportedDynamicClassLoading(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
            if (previousFactory == null) {
                System.clearProperty(FACTORY_PROPERTY_NAME);
            } else {
                System.setProperty(FACTORY_PROPERTY_NAME, previousFactory);
            }
        }
    }

    private static boolean isUnsupportedDynamicClassLoading(final Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void assertIsolatedFactoryInitialized(final ClassLoader loader,
                                                        final String expectedFactoryClassName) throws Exception {
        Class<?> logManagerClass = Class.forName("org.apache.logging.log4j.LogManager", true, loader);
        Method getFactory = logManagerClass.getMethod("getFactory");
        Object factory = getFactory.invoke(null);
        ClassLoader factoryLoader = factory.getClass().getClassLoader();

        if (isNativeImageRuntime() && factoryLoader != loader) {
            throw new TestAbortedException(
                "Native image runtime does not reload Log4j classes via isolated URLClassLoader");
        }

        assertThat(factory.getClass().getName()).isEqualTo(expectedFactoryClassName);
        assertThat(factoryLoader).isSameAs(loader);
    }

    private static URL[] classPathUrls() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toUri())
            .map(uri -> {
                try {
                    return uri.toURL();
                } catch (MalformedURLException exception) {
                    throw new IllegalStateException("Unable to convert classpath entry to URL", exception);
                }
            })
            .toArray(URL[]::new);
    }

    public static class FailingLoggerContextFactory implements LoggerContextFactory {
        public FailingLoggerContextFactory() {
            throw new IllegalStateException("configured factory constructor was exercised");
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext, final URI configLocation, final String name) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }

        @Override
        public void removeContext(final LoggerContext context) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }
    }

    public static class SuccessfulConfiguredLoggerContextFactory implements LoggerContextFactory {
        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext, final URI configLocation, final String name) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }

        @Override
        public void removeContext(final LoggerContext context) {
            throw new UnsupportedOperationException("This factory is only used during LogManager initialization");
        }
    }

    public static class ProviderLoggerContextFactory implements LoggerContextFactory {
        private final SimpleLoggerContextFactory delegate = new SimpleLoggerContextFactory();

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext) {
            return delegate.getContext(fqcn, loader, externalContext, currentContext);
        }

        @Override
        public LoggerContext getContext(final String fqcn, final ClassLoader loader, final Object externalContext,
                                        final boolean currentContext, final URI configLocation, final String name) {
            return delegate.getContext(fqcn, loader, externalContext, currentContext, configLocation, name);
        }

        @Override
        public void removeContext(final LoggerContext context) {
            delegate.removeContext(context);
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(final URL[] urls) {
            super(urls, LogManagerTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
            if (!loadsChildFirst(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException exception) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static boolean loadsChildFirst(final String name) {
            return name.startsWith("org.apache.logging.log4j.")
                || name.startsWith("org_apache_logging_log4j.log4j_api.LogManagerTest$");
        }
    }
}
