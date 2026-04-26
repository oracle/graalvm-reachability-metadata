/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.CodeSource;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0$7Test {
    private static final String NETTY_NO_UNSAFE_PROPERTY = "io.netty.noUnsafe";
    private static final String NETTY_TRY_UNSAFE_PROPERTY = "io.netty.tryUnsafe";
    private static final String LEGACY_NETTY_TRY_UNSAFE_PROPERTY = "org.jboss.netty.tryUnsafe";
    private static final String UNINITIALIZED_ARRAY_THRESHOLD_PROPERTY =
            "io.netty.uninitializedArrayAllocationThreshold";

    @Test
    void initializesInternalUnsafeLookupWhenPublicUnsafeApisAreUsedFromAFreshClassLoader() throws Exception {
        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        Assertions.assertNotNull(isolatedLibraryLocation, "Expected netty-common to have a code source");

        try (SystemPropertiesRestorer ignored = new SystemPropertiesRestorer(
                NETTY_NO_UNSAFE_PROPERTY,
                NETTY_TRY_UNSAFE_PROPERTY,
                LEGACY_NETTY_TRY_UNSAFE_PROPERTY,
                UNINITIALIZED_ARRAY_THRESHOLD_PROPERTY
        )) {
            System.clearProperty(NETTY_NO_UNSAFE_PROPERTY);
            System.clearProperty(NETTY_TRY_UNSAFE_PROPERTY);
            System.clearProperty(LEGACY_NETTY_TRY_UNSAFE_PROPERTY);
            System.setProperty(UNINITIALIZED_ARRAY_THRESHOLD_PROPERTY, "0");

            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                Class<?> platformDependentClass =
                        Class.forName("io.netty.util.internal.PlatformDependent", true, classLoader);

                Assertions.assertTrue(invokeBoolean(platformDependentClass, "hasUnsafe"));

                byte[] allocated = invokeByteArray(platformDependentClass, "allocateUninitializedArray", 32);

                Assertions.assertEquals(32, allocated.length);
                allocated[0] = 1;
                allocated[31] = 2;
                Assertions.assertEquals(1, allocated[0]);
                Assertions.assertEquals(2, allocated[31]);
            }
        }
    }

    private static URL findIsolatedLibraryLocation() {
        CodeSource codeSource = PlatformDependent.class.getProtectionDomain().getCodeSource();
        if (codeSource == null) {
            return null;
        }

        URL location = codeSource.getLocation();
        if (location == null) {
            return null;
        }

        String externalForm = location.toExternalForm();
        if (!externalForm.endsWith(".jar") && !externalForm.endsWith("/")) {
            return null;
        }
        return location;
    }

    private static boolean invokeBoolean(Class<?> type, String methodName) throws ReflectiveOperationException {
        return (Boolean) type.getMethod(methodName).invoke(null);
    }

    private static byte[] invokeByteArray(Class<?> type, String methodName, int size)
            throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, int.class);
        return (byte[]) method.invoke(null, size);
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, PlatformDependent0$7Test.class.getClassLoader());
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
