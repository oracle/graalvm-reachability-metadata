/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_all;

import io.netty.util.internal.PlatformDependent;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.CodeSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
                exercisePatchedPlatformDependent0Initializer(isolatedLibraryLocation);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
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

            if (invokeBoolean(platformDependentClass, "useDirectBufferNoCleaner")) {
                ByteBuffer noCleanerBuffer = (ByteBuffer) invoke(
                        platformDependentClass,
                        "allocateDirectNoCleaner",
                        new Class<?>[] {int.class},
                        Long.BYTES
                );
                try {
                    assertThat(noCleanerBuffer.isDirect()).isTrue();
                    assertThat(noCleanerBuffer.capacity()).isEqualTo(Long.BYTES);
                    noCleanerBuffer.putLong(0, 0x0102030405060708L);
                    assertThat(noCleanerBuffer.getLong(0)).isEqualTo(0x0102030405060708L);
                } finally {
                    invokeVoid(
                            platformDependentClass,
                            "freeDirectNoCleaner",
                            new Class<?>[] {ByteBuffer.class},
                            noCleanerBuffer
                    );
                }
            }
        } else {
            assertThat(invokeBoolean(platformDependentClass, "directBufferPreferred")).isFalse();
        }
    }

    private static void exercisePatchedPlatformDependent0Initializer(URL libraryLocation) throws Exception {
        try (PatchedPlatformDependent0ClassLoader classLoader =
                     new PatchedPlatformDependent0ClassLoader(libraryLocation)) {
            Class<?> platformDependent0Class = Class.forName(
                    "io.netty.util.internal.PlatformDependent0",
                    true,
                    classLoader
            );
            DirectBufferConstructorProbe.lastAllocatedAddress = 0L;
            Method allocateDirectNoCleanerMethod = platformDependent0Class.getDeclaredMethod(
                    "allocateDirectNoCleaner",
                    int.class
            );
            allocateDirectNoCleanerMethod.setAccessible(true);

            try {
                assertThatExceptionOfType(InvocationTargetException.class)
                        .isThrownBy(() -> allocateDirectNoCleanerMethod.invoke(null, 1))
                        .satisfies(exception -> assertThat(exception.getCause()).isInstanceOf(Error.class));
            } finally {
                long address = DirectBufferConstructorProbe.lastAllocatedAddress;
                assertThat(address).isNotZero();
                DirectBufferConstructorProbe.lastAllocatedAddress = 0L;
                HeapByteBufferArrayBaseOffsetRestorer.getUnsafe().freeMemory(address);
            }
        }
    }

    private static byte[] patchPlatformDependent0Initializer(byte[] originalClassBytes) throws IOException {
        ClassFileEditor editor = new ClassFileEditor(originalClassBytes);
        int getClassMethodRefIndex = editor.findMethodRefIndex(
                "java/lang/Object",
                "getClass",
                "()Ljava/lang/Class;"
        );
        String probeClassName = DirectBufferConstructorProbe.class.getName().replace('.', '/');
        return editor.replaceAload0GetClassWithClassLiteral(getClassMethodRefIndex, probeClassName);
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

    public static final class DirectBufferConstructorProbe {
        private static volatile long lastAllocatedAddress;

        public DirectBufferConstructorProbe(long address, int capacity) {
            lastAllocatedAddress = address;
            assertThat(capacity).isEqualTo(1);
        }
    }

    private static final class PatchedPlatformDependent0ClassLoader extends URLClassLoader {
        private static final String PLATFORM_DEPENDENT0 = "io.netty.util.internal.PlatformDependent0";
        private static final String PLATFORM_DEPENDENT0_RESOURCE = "io/netty/util/internal/PlatformDependent0.class";

        private PatchedPlatformDependent0ClassLoader(URL libraryLocation) {
            super(new URL[] {libraryLocation}, PlatformDependent0Test.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && PLATFORM_DEPENDENT0.equals(name)) {
                    loadedClass = definePatchedPlatformDependent0();
                }
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

        private Class<?> definePatchedPlatformDependent0() throws ClassNotFoundException {
            try (InputStream stream = getResourceAsStream(PLATFORM_DEPENDENT0_RESOURCE)) {
                if (stream == null) {
                    throw new ClassNotFoundException(PLATFORM_DEPENDENT0);
                }
                byte[] classBytes = patchPlatformDependent0Initializer(stream.readAllBytes());
                return defineClass(PLATFORM_DEPENDENT0, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(PLATFORM_DEPENDENT0, exception);
            }
        }
    }

    private static final class ClassFileEditor {
        private final byte[] originalClassBytes;
        private final CpInfo[] constantPool;
        private final int constantPoolCount;
        private final int constantPoolEnd;

        private ClassFileEditor(byte[] originalClassBytes) throws IOException {
            this.originalClassBytes = originalClassBytes;
            constantPoolCount = readUnsignedShort(originalClassBytes, 8);
            constantPool = new CpInfo[constantPoolCount];

            int offset = 10;
            int index = 1;
            while (index < constantPoolCount) {
                int tag = originalClassBytes[offset] & 0xFF;
                CpInfo cpInfo = new CpInfo(tag);
                constantPool[index] = cpInfo;
                offset++;
                switch (tag) {
                    case 1:
                        int length = readUnsignedShort(originalClassBytes, offset);
                        cpInfo.utf8 = new String(originalClassBytes, offset + 2, length, StandardCharsets.UTF_8);
                        offset += 2 + length;
                        break;
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        cpInfo.firstIndex = readUnsignedShort(originalClassBytes, offset);
                        offset += 2;
                        break;
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 18:
                        cpInfo.firstIndex = readUnsignedShort(originalClassBytes, offset);
                        cpInfo.secondIndex = readUnsignedShort(originalClassBytes, offset + 2);
                        offset += 4;
                        break;
                    case 3:
                    case 4:
                        offset += 4;
                        break;
                    case 5:
                    case 6:
                        offset += 8;
                        index++;
                        break;
                    case 15:
                        cpInfo.firstIndex = originalClassBytes[offset] & 0xFF;
                        cpInfo.secondIndex = readUnsignedShort(originalClassBytes, offset + 1);
                        offset += 3;
                        break;
                    default:
                        throw new IOException("Unsupported constant-pool tag: " + tag);
                }
                index++;
            }
            constantPoolEnd = offset;
        }

        private int findMethodRefIndex(String owner, String name, String descriptor) throws IOException {
            for (int index = 1; index < constantPoolCount; index++) {
                CpInfo cpInfo = constantPool[index];
                if (cpInfo != null && cpInfo.tag == 10 && owner.equals(className(cpInfo.firstIndex))) {
                    CpInfo nameAndType = constantPool[cpInfo.secondIndex];
                    if (name.equals(utf8(nameAndType.firstIndex))
                            && descriptor.equals(utf8(nameAndType.secondIndex))) {
                        return index;
                    }
                }
            }
            throw new IOException("Could not find method reference: " + owner + '.' + name + descriptor);
        }

        private byte[] replaceAload0GetClassWithClassLiteral(int methodRefIndex, String className)
                throws IOException {
            byte[] classNameBytes = className.getBytes(StandardCharsets.UTF_8);
            int utf8Index = constantPoolCount;
            int classIndex = constantPoolCount + 1;

            int patchedClassLength = originalClassBytes.length + classNameBytes.length + 8;
            ByteArrayOutputStream output = new ByteArrayOutputStream(patchedClassLength);
            DataOutputStream dataOutput = new DataOutputStream(output);
            output.write(originalClassBytes, 0, 8);
            dataOutput.writeShort(constantPoolCount + 2);
            output.write(originalClassBytes, 10, constantPoolEnd - 10);
            dataOutput.writeByte(1);
            dataOutput.writeShort(classNameBytes.length);
            dataOutput.write(classNameBytes);
            dataOutput.writeByte(7);
            dataOutput.writeShort(utf8Index);
            output.write(originalClassBytes, constantPoolEnd, originalClassBytes.length - constantPoolEnd);

            byte[] patchedClassBytes = output.toByteArray();
            int patchedConstantPoolEnd = constantPoolEnd + classNameBytes.length + 6;
            int replacementCount = replaceInstructionSequence(
                    patchedClassBytes,
                    patchedConstantPoolEnd,
                    methodRefIndex,
                    classIndex
            );
            if (replacementCount != 1) {
                throw new IOException("Expected one PlatformDependent0 getClass instruction sequence");
            }
            return patchedClassBytes;
        }

        private int replaceInstructionSequence(
                byte[] classBytes,
                int startOffset,
                int methodRefIndex,
                int classIndex
        ) {
            int replacementCount = 0;
            for (int index = startOffset; index < classBytes.length - 3; index++) {
                if ((classBytes[index] & 0xFF) == 0x2A
                        && (classBytes[index + 1] & 0xFF) == 0xB6
                        && readUnsignedShort(classBytes, index + 2) == methodRefIndex) {
                    classBytes[index] = 0x13;
                    writeUnsignedShort(classBytes, index + 1, classIndex);
                    classBytes[index + 3] = 0;
                    replacementCount++;
                }
            }
            return replacementCount;
        }

        private String className(int classInfoIndex) {
            return utf8(constantPool[classInfoIndex].firstIndex);
        }

        private String utf8(int utf8Index) {
            return constantPool[utf8Index].utf8;
        }

        private static int readUnsignedShort(byte[] data, int offset) {
            return ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
        }

        private static void writeUnsignedShort(byte[] data, int offset, int value) {
            data[offset] = (byte) (value >>> 8);
            data[offset + 1] = (byte) value;
        }
    }

    private static final class CpInfo {
        private final int tag;
        private int firstIndex;
        private int secondIndex;
        private String utf8;

        private CpInfo(int tag) {
            this.tag = tag;
        }
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
