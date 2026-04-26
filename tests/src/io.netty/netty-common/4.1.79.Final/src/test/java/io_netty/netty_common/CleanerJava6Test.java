/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CleanerJava6Test {
    @Test
    void freesDirectBuffersWithTheJava6ReflectionCleanerPath() throws Exception {
        try (SystemPropertiesRestorer ignored = new SystemPropertiesRestorer(
                "java.specification.version",
                "io.netty.noUnsafe",
                "io.netty.noPreferDirect"
        )) {
            System.setProperty("java.specification.version", "1.8");
            System.setProperty("io.netty.noUnsafe", "true");
            System.setProperty("io.netty.noPreferDirect", "false");

            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(findNettyCommonLocation())) {
                Class<?> platformDependentClass =
                        Class.forName("io.netty.util.internal.PlatformDependent", true, classLoader);

                Assertions.assertEquals(8, invokeInt(platformDependentClass, "javaVersion"));
                Assertions.assertFalse(invokeBoolean(platformDependentClass, "hasUnsafe"));
                Assertions.assertTrue(invokeBoolean(platformDependentClass, "directBufferPreferred"));

                ByteBuffer buffer = ByteBuffer.allocateDirect(32);
                invokeVoid(platformDependentClass, "freeDirectBuffer", buffer);
            }
        }
    }

    private static int invokeInt(Class<?> type, String methodName) throws ReflectiveOperationException {
        return ((Number) type.getMethod(methodName).invoke(null)).intValue();
    }

    private static boolean invokeBoolean(Class<?> type, String methodName) throws ReflectiveOperationException {
        return (Boolean) type.getMethod(methodName).invoke(null);
    }

    private static void invokeVoid(Class<?> type, String methodName, ByteBuffer buffer)
            throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, ByteBuffer.class);
        method.invoke(null, buffer);
    }

    private static URL findNettyCommonLocation() throws MalformedURLException, URISyntaxException {
        String resourceName = "io/netty/util/internal/PlatformDependent.class";
        URL resource = CleanerJava6Test.class.getClassLoader().getResource(resourceName);
        Assertions.assertNotNull(resource, "Expected netty-common on the test classpath");

        if ("jar".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            int separatorIndex = externalForm.indexOf("!/");
            return URI.create(externalForm.substring("jar:".length(), separatorIndex)).toURL();
        }

        if ("file".equals(resource.getProtocol())) {
            String externalForm = resource.toExternalForm();
            return URI.create(externalForm.substring(0, externalForm.length() - resourceName.length())).toURL();
        }

        throw new IllegalStateException("Unsupported netty-common location: " + resource);
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, CleanerJava6Test.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && name.startsWith("io.netty.")) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException ignored) {
                        loadedClass = null;
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }
    }

    private static final class SystemPropertiesRestorer implements AutoCloseable {
        private final String[] keys;
        private final String[] values;

        private SystemPropertiesRestorer(String... keys) {
            this.keys = keys;
            this.values = new String[keys.length];
            for (int i = 0; i < keys.length; i++) {
                values[i] = System.getProperty(keys[i]);
            }
        }

        @Override
        public void close() {
            for (int i = 0; i < keys.length; i++) {
                if (values[i] == null) {
                    System.clearProperty(keys[i]);
                } else {
                    System.setProperty(keys[i], values[i]);
                }
            }
        }
    }
}
