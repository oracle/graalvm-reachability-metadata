/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Set;

import com.hazelcast.map.IMap;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.HazelcastCacheMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastIMapAdapterTest {
    private static final String CACHE_NAME = "hazelcast-cache";
    private static final String CACHE_BINDER_PACKAGE = "io.micrometer.core.instrument.binder.cache.";
    private static final String HAZELCAST_CACHE_METRICS = CACHE_BINDER_PACKAGE + "HazelcastCacheMetrics";
    private static final String HAZELCAST_I_MAP_ADAPTER = CACHE_BINDER_PACKAGE + "HazelcastIMapAdapter";

    @Test
    void bindsPrimaryHazelcastIMapThroughPublicCacheBinder() {
        MeterRegistry registry = new SimpleMeterRegistry();
        IMap<String, String> cache = hazelcastMapWithoutLocalStats();

        Object monitoredCache = HazelcastCacheMetrics.monitor(registry, cache, "scenario", "primary");

        assertThat(monitoredCache).isSameAs(cache);
        assertThat(registry.find("cache.gets").tag("cache", CACHE_NAME).tag("result", "hit").functionCounter()
                .count()).isEqualTo(0.0);
        assertThat(registry.find("cache.puts").tag("cache", CACHE_NAME).functionCounter().count()).isEqualTo(0.0);
        assertThat(registry.find("cache.size").tag("cache", CACHE_NAME).gauge()).isNull();
    }

    @Test
    void bindsLegacyHazelcastIMapThroughIsolatedPublicCacheBinder() throws Exception {
        try {
            MeterRegistry registry = new SimpleMeterRegistry();
            Object cache = legacyHazelcastMapWithoutLocalStats(registry);

            assertThat(cache).isNotNull();
            assertThat(registry.find("cache.gets").tag("cache", CACHE_NAME).tag("result", "hit").functionCounter()
                    .count()).isEqualTo(0.0);
            assertThat(registry.find("cache.puts").tag("cache", CACHE_NAME).functionCounter().count()).isEqualTo(0.0);
            assertThat(registry.find("cache.size").tag("cache", CACHE_NAME).gauge()).isNull();
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Error) {
                rethrowIfNotUnsupportedFeatureError((Error) cause);
                return;
            }
            throw exception;
        } catch (Error error) {
            rethrowIfNotUnsupportedFeatureError(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static IMap<String, String> hazelcastMapWithoutLocalStats() {
        return (IMap<String, String>) Proxy.newProxyInstance(HazelcastIMapAdapterTest.class.getClassLoader(),
                new Class<?>[] { IMap.class }, (proxy, method, args) -> handleMapInvocation(proxy, method, args));
    }

    private static Object legacyHazelcastMapWithoutLocalStats(MeterRegistry registry) throws Exception {
        try (HazelcastFallbackClassLoader classLoader = new HazelcastFallbackClassLoader(
                legacyHazelcastClasspathUrls(), HazelcastIMapAdapterTest.class.getClassLoader())) {
            Class<?> legacyIMap = classLoader.loadClass("com.hazelcast.core.IMap");
            Object cache = Proxy.newProxyInstance(classLoader, new Class<?>[] { legacyIMap },
                    (proxy, method, args) -> handleMapInvocation(proxy, method, args));
            Class<?> cacheMetrics = classLoader.loadClass(HAZELCAST_CACHE_METRICS);
            Method monitor = cacheMetrics.getMethod("monitor", MeterRegistry.class, Object.class, String[].class);

            return monitor.invoke(null, registry, cache, new String[] { "scenario", "fallback" });
        }
    }

    private static URL[] legacyHazelcastClasspathUrls() throws Exception {
        String classpath = System.getProperty("hazelcastLegacyClasspath");
        assertThat(classpath).isNotBlank();
        String[] entries = classpath.split(File.pathSeparator);
        URL[] urls = new URL[entries.length];
        for (int index = 0; index < entries.length; index++) {
            urls[index] = new File(entries[index]).toURI().toURL();
        }
        return urls;
    }

    private static Object handleMapInvocation(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "getName":
                return CACHE_NAME;
            case "getLocalMapStats":
                return null;
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            case "toString":
                return CACHE_NAME;
            default:
                return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == char.class) {
            return '\0';
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        return null;
    }

    private static void rethrowIfNotUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class HazelcastFallbackClassLoader extends URLClassLoader {
        private static final Set<String> ISOLATED_CLASSES = Set.of(
                HAZELCAST_CACHE_METRICS,
                HAZELCAST_I_MAP_ADAPTER,
                HAZELCAST_I_MAP_ADAPTER + "$LocalMapStats",
                HAZELCAST_I_MAP_ADAPTER + "$NearCacheStats"
        );

        private HazelcastFallbackClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = loadUnresolvedClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> loadUnresolvedClass(String name) throws ClassNotFoundException {
            if (isPrimaryHazelcastClass(name)) {
                throw new ClassNotFoundException(name);
            }
            if (ISOLATED_CLASSES.contains(name)) {
                return defineClassFromParentResource(name);
            }
            if (name.startsWith("com.hazelcast.")) {
                try {
                    return findClass(name);
                } catch (ClassNotFoundException exception) {
                    return super.loadClass(name, false);
                }
            }
            return super.loadClass(name, false);
        }

        private boolean isPrimaryHazelcastClass(String name) {
            return "com.hazelcast.map.IMap".equals(name)
                    || "com.hazelcast.map.LocalMapStats".equals(name)
                    || "com.hazelcast.nearcache.NearCacheStats".equals(name);
        }

        private Class<?> defineClassFromParentResource(String name) throws ClassNotFoundException {
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = inputStream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
