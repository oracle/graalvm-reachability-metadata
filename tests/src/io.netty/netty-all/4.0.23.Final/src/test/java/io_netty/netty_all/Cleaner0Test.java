/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.CodeSource;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class Cleaner0Test {
    private static final String NETTY_NO_UNSAFE_PROPERTY = "io.netty.noUnsafe";
    private static final String NETTY_TRY_UNSAFE_PROPERTY = "io.netty.tryUnsafe";
    private static final String LEGACY_NETTY_TRY_UNSAFE_PROPERTY = "org.jboss.netty.tryUnsafe";

    @Test
    void freesDirectByteBufferThroughFreshPlatformDependentInitialization() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocateDirect(16);
        buffer.putLong(0, 0x0102030405060708L);

        Assertions.assertTrue(buffer.isDirect());
        Assertions.assertEquals(0x0102030405060708L, buffer.getLong(0));

        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        if (isolatedLibraryLocation == null) {
            Assertions.assertDoesNotThrow(() -> PlatformDependent.freeDirectBuffer(buffer));
            return;
        }

        runFreeDirectBufferThroughIsolatedNettyInitialization(isolatedLibraryLocation, buffer);
    }

    private static void runFreeDirectBufferThroughIsolatedNettyInitialization(URL isolatedLibraryLocation,
            ByteBuffer buffer) throws Exception {
        try (SystemPropertiesRestorer ignored = new SystemPropertiesRestorer(
                NETTY_NO_UNSAFE_PROPERTY,
                NETTY_TRY_UNSAFE_PROPERTY,
                LEGACY_NETTY_TRY_UNSAFE_PROPERTY
        )) {
            System.clearProperty(NETTY_NO_UNSAFE_PROPERTY);
            System.setProperty(NETTY_TRY_UNSAFE_PROPERTY, "true");
            System.setProperty(LEGACY_NETTY_TRY_UNSAFE_PROPERTY, "true");

            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                Class<?> platformDependentClass =
                        Class.forName("io.netty.util.internal.PlatformDependent", true, classLoader);

                Assertions.assertTrue(invokeBoolean(platformDependentClass, "hasUnsafe"));
                Assertions.assertDoesNotThrow(() -> invokeFreeDirectBuffer(platformDependentClass, buffer));
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

    private static void invokeFreeDirectBuffer(Class<?> platformDependentClass, ByteBuffer buffer)
            throws ReflectiveOperationException {
        Method method = platformDependentClass.getMethod("freeDirectBuffer", ByteBuffer.class);
        method.invoke(null, buffer);
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, Cleaner0Test.class.getClassLoader());
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
