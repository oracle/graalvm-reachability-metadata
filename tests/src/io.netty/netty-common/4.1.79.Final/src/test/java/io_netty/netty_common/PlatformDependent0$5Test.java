/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.CodeSource;
import java.security.PrivilegedAction;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0$5Test {
    private static final String NETTY_MAX_DIRECT_MEMORY_PROPERTY = "io.netty.maxDirectMemory";
    private static final String NETTY_NO_UNSAFE_PROPERTY = "io.netty.noUnsafe";

    @Test
    void runsThePrivilegedDirectByteBufferConstructorLookup() throws Throwable {
        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        if (isolatedLibraryLocation != null) {
            runLookupThroughFreshPlatformDependentInitialization(isolatedLibraryLocation);
            return;
        }

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(1);
        Assertions.assertTrue(directBuffer.isDirect(), "Expected a direct buffer");

        Class<?> privilegedActionClass = Class.forName("io.netty.util.internal.PlatformDependent0$5");
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(privilegedActionClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(
                privilegedActionClass,
                MethodType.methodType(void.class, ByteBuffer.class)
        );
        @SuppressWarnings("unchecked")
        PrivilegedAction<Object> privilegedAction = (PrivilegedAction<Object>) constructor.invoke(directBuffer);

        Object result = AccessController.doPrivileged(privilegedAction);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(
                result instanceof Constructor<?> || result instanceof Throwable,
                () -> "Unexpected lookup result type: " + result.getClass().getName()
        );
    }

    private static void runLookupThroughFreshPlatformDependentInitialization(URL isolatedLibraryLocation) throws Exception {
        try (SystemPropertiesRestorer ignored = new SystemPropertiesRestorer(
                NETTY_MAX_DIRECT_MEMORY_PROPERTY,
                NETTY_NO_UNSAFE_PROPERTY
        )) {
            System.setProperty(NETTY_MAX_DIRECT_MEMORY_PROPERTY, "1048576");
            System.clearProperty(NETTY_NO_UNSAFE_PROPERTY);

            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                Class<?> platformDependentClass =
                        Class.forName("io.netty.util.internal.PlatformDependent", true, classLoader);

                Assertions.assertTrue(invokeBoolean(platformDependentClass, "hasUnsafe"));

                boolean useDirectBufferNoCleaner = invokeBoolean(platformDependentClass, "useDirectBufferNoCleaner");
                if (useDirectBufferNoCleaner) {
                    ByteBuffer currentBuffer = invokeByteBuffer(platformDependentClass, "allocateDirectNoCleaner", 16);
                    try {
                        Assertions.assertTrue(currentBuffer.isDirect());
                        Assertions.assertEquals(16, currentBuffer.capacity());
                    } finally {
                        invokeVoid(platformDependentClass, "freeDirectNoCleaner", currentBuffer);
                    }
                } else {
                    ByteBuffer directBuffer = ByteBuffer.allocateDirect(16);
                    Assertions.assertTrue(directBuffer.isDirect());
                    invokeVoid(platformDependentClass, "freeDirectBuffer", directBuffer);
                }
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

    private static ByteBuffer invokeByteBuffer(Class<?> type, String methodName, int capacity)
            throws ReflectiveOperationException {
        return (ByteBuffer) type.getMethod(methodName, int.class).invoke(null, capacity);
    }

    private static void invokeVoid(Class<?> type, String methodName, ByteBuffer buffer)
            throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, ByteBuffer.class);
        method.invoke(null, buffer);
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, PlatformDependent0$5Test.class.getClassLoader());
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
