/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

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

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginLoaderTest {
    private static final String PLUGIN_SWITCH_RESOURCE =
            "mockito-extensions/org.mockito.plugins.PluginSwitch";
    private static final String MOCK_RESOLVER_RESOURCE =
            "mockito-extensions/org.mockito.plugins.MockResolver";
    private static final String MISSING_PLUGIN_CLASS =
            "org_mockito.mockito_core.DoesNotExistPlugin";

    @Test
    void pluginDiscoveryFailuresUseProxyFallbacksForSingleAndMultiplePlugins() throws Exception {
        try {
            loadMockitoPluginsWithFailingPluginDiscovery();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void loadMockitoPluginsWithFailingPluginDiscovery() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        URL[] classPathUrls = currentClassPathUrls();

        try (FailingPluginClassLoader classLoader = new FailingPluginClassLoader(classPathUrls)) {
            Thread.currentThread().setContextClassLoader(classLoader);

            Class<?> accessClass = Class.forName(
                    IsolatedMockitoPluginAccess.class.getName(), true, classLoader);
            Object access = accessClass.getDeclaredConstructor().newInstance();
            Object pluginClassName = accessClass.getMethod("call").invoke(access);

            assertThat(pluginClassName)
                    .isEqualTo("org.mockito.internal.configuration.plugins.DefaultMockitoPlugins");
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error
                    && NativeImageSupport.isUnsupportedFeatureError((Error) cause)) {
                return;
            }
            throw exception;
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static URL[] currentClassPathUrls() throws IOException {
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

    private static final class FailingPluginClassLoader extends URLClassLoader {
        private FailingPluginClassLoader(URL[] urls) {
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
            return name.startsWith("org.mockito.")
                    || name.startsWith(PluginLoaderTest.class.getName());
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            List<URL> resources = new ArrayList<>();
            if (PLUGIN_SWITCH_RESOURCE.equals(name) || MOCK_RESOLVER_RESOURCE.equals(name)) {
                resources.add(inMemoryPluginResource(name, MISSING_PLUGIN_CLASS));
            }
            resources.addAll(Collections.list(super.getResources(name)));
            return Collections.enumeration(resources);
        }
    }

    public static final class IsolatedMockitoPluginAccess implements Callable<String> {
        @Override
        public String call() {
            Object plugins = Mockito.framework().getPlugins();
            return plugins.getClass().getName();
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
