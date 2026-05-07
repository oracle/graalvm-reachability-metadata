/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.simple.SimpleLoggerContextFactory;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.spi.LoggerContextFactory;
import org.apache.logging.log4j.spi.ThreadContextMap;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

public class ThreadContextTest {
    private static final String THREAD_CONTEXT_MAP_PROPERTY_NAME = "log4j2.threadContextMap";
    private static final String FACTORY_PROPERTY_NAME = "log4j2.loggerContextFactory";
    private static final String THREAD_CONTEXT_CLASS_NAME = "org.apache.logging.log4j.ThreadContext";

    @Test
    void initializesConfiguredThreadContextMap() throws Exception {
        assertIsolatedThreadContextUsesMap(
            ConfiguredThreadContextMap.class.getName(),
            null,
            "configured-key",
            "configured:configured-value");
    }

    @Test
    void initializesProviderThreadContextMap(@TempDir final Path tempDir) throws Exception {
        Path providerRoot = writeProviderResource(tempDir);
        assertIsolatedThreadContextUsesMap(
            null,
            providerRoot,
            "provider-key",
            "provider:provider-value");
    }

    private static void assertIsolatedThreadContextUsesMap(final String configuredThreadContextMap,
                                                          final Path providerRoot,
                                                          final String key,
                                                          final String expectedValue) throws Exception {
        String previousThreadContextMap = System.getProperty(THREAD_CONTEXT_MAP_PROPERTY_NAME);
        String previousFactory = System.getProperty(FACTORY_PROPERTY_NAME);
        ClassLoader previousClassLoader = Thread.currentThread().getContextClassLoader();
        try (ChildFirstUrlClassLoader loader = new ChildFirstUrlClassLoader(classPathUrls(providerRoot))) {
            if (configuredThreadContextMap == null) {
                System.clearProperty(THREAD_CONTEXT_MAP_PROPERTY_NAME);
            } else {
                System.setProperty(THREAD_CONTEXT_MAP_PROPERTY_NAME, configuredThreadContextMap);
            }
            if (providerRoot != null) {
                System.setProperty(FACTORY_PROPERTY_NAME, ProviderLoggerContextFactory.class.getName());
            }
            Thread.currentThread().setContextClassLoader(loader);
            assertThreadContextMapStoresExpectedValue(loader, key, expectedValue);
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
            if (previousThreadContextMap == null) {
                System.clearProperty(THREAD_CONTEXT_MAP_PROPERTY_NAME);
            } else {
                System.setProperty(THREAD_CONTEXT_MAP_PROPERTY_NAME, previousThreadContextMap);
            }
            if (previousFactory == null) {
                System.clearProperty(FACTORY_PROPERTY_NAME);
            } else {
                System.setProperty(FACTORY_PROPERTY_NAME, previousFactory);
            }
        }
    }

    private static void assertThreadContextMapStoresExpectedValue(final ClassLoader loader,
                                                                 final String key,
                                                                 final String expectedValue) throws Exception {
        Class<?> threadContextClass = Class.forName(THREAD_CONTEXT_CLASS_NAME, true, loader);
        Method put = threadContextClass.getMethod("put", String.class, String.class);
        Method get = threadContextClass.getMethod("get", String.class);

        put.invoke(null, key, expectedValue.substring(expectedValue.indexOf(':') + 1));
        Object actualValue = get.invoke(null, key);
        if (isNativeImageRuntime() && !expectedValue.equals(actualValue)) {
            throw new TestAbortedException(
                "Native image runtime does not reload Log4j ThreadContext implementations via isolated URLClassLoader");
        }
        assertThat(actualValue).isEqualTo(expectedValue);
    }

    private static Path writeProviderResource(final Path tempDir) throws IOException {
        Path metaInf = tempDir.resolve("META-INF");
        Files.createDirectories(metaInf);
        Path providerResource = metaInf.resolve("log4j-provider.properties");
        Files.writeString(providerResource, providerProperties(), StandardCharsets.ISO_8859_1);
        return tempDir;
    }

    private static String providerProperties() {
        StringWriter writer = new StringWriter();
        try (PrintWriter printWriter = new PrintWriter(writer)) {
            printWriter.println("Log4jAPIVersion=2.0.0");
            printWriter.println("FactoryPriority=100");
            printWriter.println("LoggerContextFactory=" + ProviderLoggerContextFactory.class.getName());
            printWriter.println("ThreadContextMap=" + ProviderThreadContextMap.class.getName());
        }
        return writer.toString();
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

    private static URL[] classPathUrls(final Path providerRoot) {
        URL[] classPathUrls = Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .map(entry -> Path.of(entry).toUri())
            .map(uri -> {
                try {
                    return uri.toURL();
                } catch (MalformedURLException exception) {
                    throw new IllegalStateException("Unable to convert classpath entry to URL", exception);
                }
            })
            .toArray(URL[]::new);
        if (providerRoot == null) {
            return classPathUrls;
        }

        URL providerUrl;
        try {
            providerUrl = providerRoot.toUri().toURL();
        } catch (MalformedURLException exception) {
            throw new IllegalStateException("Unable to convert provider directory to URL", exception);
        }
        URL[] urls = new URL[classPathUrls.length + 1];
        urls[0] = providerUrl;
        System.arraycopy(classPathUrls, 0, urls, 1, classPathUrls.length);
        return urls;
    }

    public static class ConfiguredThreadContextMap extends PrefixingThreadContextMap {
        public ConfiguredThreadContextMap() {
            super("configured");
        }
    }

    public static class ProviderThreadContextMap extends PrefixingThreadContextMap {
        public ProviderThreadContextMap() {
            super("provider");
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

    public abstract static class PrefixingThreadContextMap implements ThreadContextMap {
        private final String prefix;
        private final Map<String, String> values = new HashMap<>();

        public PrefixingThreadContextMap(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void put(final String key, final String value) {
            values.put(key, prefix + ':' + value);
        }

        @Override
        public String get(final String key) {
            return values.get(key);
        }

        @Override
        public void remove(final String key) {
            values.remove(key);
        }

        @Override
        public void clear() {
            values.clear();
        }

        @Override
        public boolean containsKey(final String key) {
            return values.containsKey(key);
        }

        @Override
        public Map<String, String> getCopy() {
            return new HashMap<>(values);
        }

        @Override
        public Map<String, String> getImmutableMapOrNull() {
            if (values.isEmpty()) {
                return null;
            }
            return Collections.unmodifiableMap(values);
        }

        @Override
        public boolean isEmpty() {
            return values.isEmpty();
        }
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(final URL[] urls) {
            super(urls, ThreadContextTest.class.getClassLoader());
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
            String testClassName = ThreadContextTest.class.getName();
            return name.startsWith("org.apache.logging.log4j.")
                || name.equals(testClassName)
                || name.startsWith(testClassName + '$');
        }
    }
}
