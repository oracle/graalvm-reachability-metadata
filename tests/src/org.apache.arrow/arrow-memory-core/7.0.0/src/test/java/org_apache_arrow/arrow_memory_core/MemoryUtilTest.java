/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_arrow.arrow_memory_core;

import org.apache.arrow.memory.util.MemoryUtil;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MemoryUtilTest {
    private static final String DIRECT_BUFFER_CONSTRUCTOR_FIELD_NAME = "DIRECT_BUFFER_CONSTRUCTOR";
    private static final String MEMORY_UTIL_CLASS_NAME = "org.apache.arrow.memory.util.MemoryUtil";
    private static final String DIRECT_BUFFER_LOOKUP_ACTION_CLASS_NAME = MEMORY_UTIL_CLASS_NAME + "$2";
    private static final String INTEGER_INTERNAL_NAME = "java/lang/Integer";
    private static final String LONG_INTERNAL_NAME = "java/lang/Long";
    private static final String DYNAMIC_LOADING_PROBE_CLASS_NAME =
            "org_apache_arrow.arrow_memory_core.MemoryUtilDynamicLoadingProbe";
    private static final String DYNAMIC_LOADING_PROBE_CLASS_BYTES = """
            yv66vgAAADQADwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW
            BwAIAQBAb3JnX2FwYWNoZV9hcnJvdy9hcnJvd19tZW1vcnlfY29yZS9NZW1vcnlVdGlsRHluYW1p
            Y0xvYWRpbmdQcm9iZQEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAAVwcm9iZQEAAygpWgEAClNv
            dXJjZUZpbGUBACJNZW1vcnlVdGlsRHluYW1pY0xvYWRpbmdQcm9iZS5qYXZhACEABwACAAAAAAAC
            AAEABQAGAAEACQAAAB0AAQABAAAABSq3AAGxAAAAAQAKAAAABgABAAAAAgAJAAsADAABAAkAAAAa
            AAEAAAAAAAIErAAAAAEACgAAAAYAAQAAAAQAAQANAAAAAgAO
            """;

    @Test
    void classInitializerInvokesDirectBufferConstructorWhenCompatibleSignatureIsAvailable() throws Exception {
        boolean useLongLongConstructor = !hasDirectBufferConstructor(long.class, int.class);
        if (useLongLongConstructor) {
            assertThat(hasDirectBufferConstructor(long.class, long.class)).isTrue();
        }
        PatchedMemoryUtilClassLoader classLoader = new PatchedMemoryUtilClassLoader(
                MemoryUtilTest.class.getClassLoader(), useLongLongConstructor);

        try {
            classLoader.defineDynamicLoadingProbeClass();
            Class<?> reloadedMemoryUtil = Class.forName(MEMORY_UTIL_CLASS_NAME, true, classLoader);

            assertThat(reloadedMemoryUtil.getClassLoader()).isSameAs(classLoader);
            assertThat(reloadedMemoryUtil.getField("UNSAFE").get(null)).isNotNull();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void directBufferCreatesByteBufferViewForMemoryAddressWhenConstructorIsAvailable() throws Exception {
        Constructor<?> compatibleConstructor = findCompatibleDirectBufferConstructor();
        Object previousConstructor = replaceDirectBufferConstructor(compatibleConstructor);
        int capacity = 8;
        long address = MemoryUtil.UNSAFE.allocateMemory(capacity);
        try {
            MemoryUtil.UNSAFE.setMemory(address, capacity, (byte) 0);

            ByteBuffer buffer = MemoryUtil.directBuffer(address, capacity);

            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(capacity);
            assertThat(MemoryUtil.getByteBufferAddress(buffer)).isEqualTo(address);

            buffer.put(0, (byte) 42);
            assertThat(MemoryUtil.UNSAFE.getByte(address)).isEqualTo((byte) 42);
        } finally {
            MemoryUtil.UNSAFE.freeMemory(address);
            replaceDirectBufferConstructor(previousConstructor);
        }
    }

    @Test
    void directBufferRejectsNegativeCapacityBeforeInvokingConstructor() throws Exception {
        Constructor<?> compatibleConstructor = findCompatibleDirectBufferConstructor();
        Object previousConstructor = replaceDirectBufferConstructor(compatibleConstructor);
        try {
            assertThatThrownBy(() -> MemoryUtil.directBuffer(1L, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Capacity is negative");
        } finally {
            replaceDirectBufferConstructor(previousConstructor);
        }
    }

    private static Constructor<?> findCompatibleDirectBufferConstructor() throws Exception {
        Class<? extends ByteBuffer> directBufferClass = ByteBuffer.allocateDirect(0).getClass();
        try {
            Constructor<?> constructor = directBufferClass.getDeclaredConstructor(long.class, int.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (NoSuchMethodException e) {
            Constructor<?> constructor = directBufferClass.getDeclaredConstructor(long.class, long.class);
            constructor.setAccessible(true);
            return constructor;
        }
    }

    private static boolean hasDirectBufferConstructor(Class<?> firstParameterType, Class<?> secondParameterType) {
        Class<? extends ByteBuffer> directBufferClass = ByteBuffer.allocateDirect(0).getClass();
        try {
            directBufferClass.getDeclaredConstructor(firstParameterType, secondParameterType);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static Object replaceDirectBufferConstructor(Object constructor) throws Exception {
        Field field = MemoryUtil.class.getDeclaredField(DIRECT_BUFFER_CONSTRUCTOR_FIELD_NAME);
        Object staticFieldBase = MemoryUtil.UNSAFE.staticFieldBase(field);
        long staticFieldOffset = MemoryUtil.UNSAFE.staticFieldOffset(field);
        Object previousValue = MemoryUtil.UNSAFE.getObject(staticFieldBase, staticFieldOffset);
        MemoryUtil.UNSAFE.putObjectVolatile(staticFieldBase, staticFieldOffset, constructor);
        return previousValue;
    }

    private static final class PatchedMemoryUtilClassLoader extends ClassLoader {
        private static final int CONSTANT_UTF8_TAG = 1;
        private static final int CONSTANT_INTEGER_TAG = 3;
        private static final int CONSTANT_FLOAT_TAG = 4;
        private static final int CONSTANT_LONG_TAG = 5;
        private static final int CONSTANT_DOUBLE_TAG = 6;
        private static final int CONSTANT_CLASS_TAG = 7;
        private static final int CONSTANT_STRING_TAG = 8;
        private static final int CONSTANT_FIELD_REF_TAG = 9;
        private static final int CONSTANT_METHOD_REF_TAG = 10;
        private static final int CONSTANT_INTERFACE_METHOD_REF_TAG = 11;
        private static final int CONSTANT_NAME_AND_TYPE_TAG = 12;
        private static final int CONSTANT_METHOD_HANDLE_TAG = 15;
        private static final int CONSTANT_METHOD_TYPE_TAG = 16;
        private static final int CONSTANT_DYNAMIC_TAG = 17;
        private static final int CONSTANT_INVOKE_DYNAMIC_TAG = 18;
        private static final int CONSTANT_MODULE_TAG = 19;
        private static final int CONSTANT_PACKAGE_TAG = 20;

        private final boolean useLongLongConstructor;

        private PatchedMemoryUtilClassLoader(ClassLoader parent, boolean useLongLongConstructor) {
            super(parent);
            this.useLongLongConstructor = useLongLongConstructor;
        }

        private Class<?> defineDynamicLoadingProbeClass() {
            byte[] classBytes = Base64.getMimeDecoder().decode(DYNAMIC_LOADING_PROBE_CLASS_BYTES);
            return defineClass(DYNAMIC_LOADING_PROBE_CLASS_NAME, classBytes, 0, classBytes.length);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && shouldLoadMemoryUtilClass(name)) {
                    loadedClass = findClass(name);
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

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!shouldLoadMemoryUtilClass(name)) {
                throw new ClassNotFoundException(name);
            }
            try {
                byte[] classBytes = readClassBytes(name);
                if (useLongLongConstructor && DIRECT_BUFFER_LOOKUP_ACTION_CLASS_NAME.equals(name)) {
                    classBytes = patchIntegerClassReferenceToLong(classBytes);
                }
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }

        private static boolean shouldLoadMemoryUtilClass(String name) {
            return MEMORY_UTIL_CLASS_NAME.equals(name) || name.startsWith(MEMORY_UTIL_CLASS_NAME + "$");
        }

        private byte[] readClassBytes(String className) throws IOException {
            String resourceName = className.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resourceName)) {
                if (input == null) {
                    throw new IOException("Cannot find class bytes for " + className);
                }
                return input.readAllBytes();
            }
        }

        private static byte[] patchIntegerClassReferenceToLong(byte[] originalClassBytes) throws IOException {
            byte[] classBytes = originalClassBytes.clone();
            int constantPoolCount = readUnsignedShort(classBytes, 8);
            int[] tags = new int[constantPoolCount];
            int[] offsets = new int[constantPoolCount];
            int integerUtf8Index = -1;
            int longUtf8Index = -1;
            int offset = 10;

            for (int index = 1; index < constantPoolCount; index++) {
                int tag = classBytes[offset] & 0xff;
                tags[index] = tag;
                offsets[index] = offset;
                switch (tag) {
                    case CONSTANT_UTF8_TAG:
                        int length = readUnsignedShort(classBytes, offset + 1);
                        String value = new String(classBytes, offset + 3, length, StandardCharsets.UTF_8);
                        if (INTEGER_INTERNAL_NAME.equals(value)) {
                            integerUtf8Index = index;
                        } else if (LONG_INTERNAL_NAME.equals(value)) {
                            longUtf8Index = index;
                        }
                        offset += 3 + length;
                        break;
                    case CONSTANT_INTEGER_TAG:
                    case CONSTANT_FLOAT_TAG:
                    case CONSTANT_FIELD_REF_TAG:
                    case CONSTANT_METHOD_REF_TAG:
                    case CONSTANT_INTERFACE_METHOD_REF_TAG:
                    case CONSTANT_NAME_AND_TYPE_TAG:
                    case CONSTANT_DYNAMIC_TAG:
                    case CONSTANT_INVOKE_DYNAMIC_TAG:
                        offset += 5;
                        break;
                    case CONSTANT_LONG_TAG:
                    case CONSTANT_DOUBLE_TAG:
                        offset += 9;
                        index++;
                        break;
                    case CONSTANT_CLASS_TAG:
                    case CONSTANT_STRING_TAG:
                    case CONSTANT_METHOD_TYPE_TAG:
                    case CONSTANT_MODULE_TAG:
                    case CONSTANT_PACKAGE_TAG:
                        offset += 3;
                        break;
                    case CONSTANT_METHOD_HANDLE_TAG:
                        offset += 4;
                        break;
                    default:
                        throw new IOException("Unsupported constant pool tag " + tag);
                }
            }

            assertThat(integerUtf8Index).isPositive();
            assertThat(longUtf8Index).isPositive();
            for (int index = 1; index < constantPoolCount; index++) {
                if (tags[index] == CONSTANT_CLASS_TAG) {
                    int nameIndex = readUnsignedShort(classBytes, offsets[index] + 1);
                    if (nameIndex == integerUtf8Index) {
                        writeUnsignedShort(classBytes, offsets[index] + 1, longUtf8Index);
                    }
                }
            }
            return classBytes;
        }

        private static int readUnsignedShort(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
        }

        private static void writeUnsignedShort(byte[] bytes, int offset, int value) {
            bytes[offset] = (byte) (value >>> 8);
            bytes[offset + 1] = (byte) value;
        }
    }
}
