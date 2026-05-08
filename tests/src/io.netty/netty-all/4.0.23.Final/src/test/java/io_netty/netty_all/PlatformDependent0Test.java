/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.security.CodeSource;

import static org.assertj.core.api.Assertions.assertThat;

public class PlatformDependent0Test {
    @Test
    void initializesUnsafePlatformSupportAndAccessesDirectMemory() throws Exception {
        URL isolatedLibraryLocation = findIsolatedLibraryLocation();
        if (isolatedLibraryLocation != null) {
            try (NettyIsolatedClassLoader classLoader = new NettyIsolatedClassLoader(isolatedLibraryLocation)) {
                Class<?> platformDependentClass;
                try (HeapByteBufferArrayBaseOffsetRestorer ignored =
                             HeapByteBufferArrayBaseOffsetRestorer.zeroOffset()) {
                    platformDependentClass = Class.forName(
                            "io.netty.util.internal.PlatformDependent",
                            true,
                            classLoader
                    );
                }
                exerciseInitializedPlatformDependent(platformDependentClass);
            }
            return;
        }

        try (HeapByteBufferArrayBaseOffsetRestorer ignored = HeapByteBufferArrayBaseOffsetRestorer.zeroOffset()) {
            PlatformDependent.hasUnsafe();
        }
        exerciseInitializedPlatformDependent(PlatformDependent.class);
    }

    private static void exerciseInitializedPlatformDependent(Class<?> platformDependentClass) throws Exception {
        assertThat(invokeInt(platformDependentClass, "javaVersion")).isGreaterThanOrEqualTo(6);

        boolean hasUnsafe = invokeBoolean(platformDependentClass, "hasUnsafe");
        if (hasUnsafe) {
            ByteBuffer directBuffer = ByteBuffer.allocateDirect(Long.BYTES);
            long directBufferAddress = invokeLong(
                    platformDependentClass,
                    "directBufferAddress",
                    new Class<?>[] {ByteBuffer.class},
                    directBuffer
            );
            assertThat(directBufferAddress).isNotZero();

            long memoryAddress = invokeLong(
                    platformDependentClass,
                    "allocateMemory",
                    new Class<?>[] {long.class},
                    (long) Long.BYTES
            );
            try {
                long expectedLongValue = 0x0102030405060708L;
                invokeVoid(
                        platformDependentClass,
                        "putLong",
                        new Class<?>[] {long.class, long.class},
                        memoryAddress,
                        expectedLongValue
                );
                assertThat(invokeLong(
                        platformDependentClass,
                        "getLong",
                        new Class<?>[] {long.class},
                        memoryAddress
                )).isEqualTo(expectedLongValue);

                int expectedIntValue = 0x11223344;
                invokeVoid(
                        platformDependentClass,
                        "putInt",
                        new Class<?>[] {long.class, int.class},
                        memoryAddress,
                        expectedIntValue
                );
                assertThat(invokeInt(
                        platformDependentClass,
                        "getInt",
                        new Class<?>[] {long.class},
                        memoryAddress
                )).isEqualTo(expectedIntValue);

                byte expectedByteValue = 0x55;
                invokeVoid(
                        platformDependentClass,
                        "putByte",
                        new Class<?>[] {long.class, byte.class},
                        memoryAddress,
                        expectedByteValue
                );
                assertThat(invokeByte(
                        platformDependentClass,
                        "getByte",
                        new Class<?>[] {long.class},
                        memoryAddress
                )).isEqualTo(expectedByteValue);
            } finally {
                invokeVoid(platformDependentClass, "freeMemory", new Class<?>[] {long.class}, memoryAddress);
            }
        } else {
            assertThat(invokeBoolean(platformDependentClass, "directBufferPreferred")).isFalse();
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
        if (!externalForm.contains("netty-all") && !externalForm.contains("netty_all")) {
            return null;
        }
        return location;
    }

    private static boolean invokeBoolean(Class<?> type, String methodName) throws ReflectiveOperationException {
        return (Boolean) type.getMethod(methodName).invoke(null);
    }

    private static int invokeInt(Class<?> type, String methodName) throws ReflectiveOperationException {
        return (Integer) type.getMethod(methodName).invoke(null);
    }

    private static int invokeInt(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        return (Integer) invoke(type, methodName, parameterTypes, arguments);
    }

    private static byte invokeByte(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        return (Byte) invoke(type, methodName, parameterTypes, arguments);
    }

    private static long invokeLong(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        return (Long) invoke(type, methodName, parameterTypes, arguments);
    }

    private static void invokeVoid(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        invoke(type, methodName, parameterTypes, arguments);
    }

    private static Object invoke(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... arguments)
            throws ReflectiveOperationException {
        Method method = type.getMethod(methodName, parameterTypes);
        return method.invoke(null, arguments);
    }

    private static final class NettyIsolatedClassLoader extends URLClassLoader {
        private NettyIsolatedClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, PlatformDependent0Test.class.getClassLoader());
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

    private static final class HeapByteBufferArrayBaseOffsetRestorer implements AutoCloseable {
        private final Unsafe unsafe;
        private final Object fieldBase;
        private final long fieldOffset;
        private final long originalValue;

        private HeapByteBufferArrayBaseOffsetRestorer(
                Unsafe unsafe,
                Object fieldBase,
                long fieldOffset,
                long originalValue
        ) {
            this.unsafe = unsafe;
            this.fieldBase = fieldBase;
            this.fieldOffset = fieldOffset;
            this.originalValue = originalValue;
        }

        private static HeapByteBufferArrayBaseOffsetRestorer zeroOffset() {
            try {
                Unsafe unsafe = getUnsafe();
                Class<?> heapByteBufferClass = Class.forName("java.nio.HeapByteBuffer", true, null);
                Field arrayBaseOffsetField = heapByteBufferClass.getDeclaredField("ARRAY_BASE_OFFSET");
                Object fieldBase = unsafe.staticFieldBase(arrayBaseOffsetField);
                long fieldOffset = unsafe.staticFieldOffset(arrayBaseOffsetField);
                long originalValue = unsafe.getLong(fieldBase, fieldOffset);
                unsafe.putLong(fieldBase, fieldOffset, 0L);
                return new HeapByteBufferArrayBaseOffsetRestorer(unsafe, fieldBase, fieldOffset, originalValue);
            } catch (Throwable ignored) {
                return new HeapByteBufferArrayBaseOffsetRestorer(null, null, 0L, 0L);
            }
        }

        private static Unsafe getUnsafe() throws ReflectiveOperationException {
            Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            return (Unsafe) unsafeField.get(null);
        }

        @Override
        public void close() {
            if (unsafe != null) {
                unsafe.putLong(fieldBase, fieldOffset, originalValue);
            }
        }
    }
}
