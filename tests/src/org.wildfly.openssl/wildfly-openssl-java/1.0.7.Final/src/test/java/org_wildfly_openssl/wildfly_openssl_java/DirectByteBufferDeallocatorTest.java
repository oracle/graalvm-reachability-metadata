/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;
import org.wildfly.openssl.util.DirectByteBufferDeallocator;

import sun.misc.Unsafe;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class DirectByteBufferDeallocatorTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void freeAcceptsDirectByteBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);
        buffer.putInt(0x12345678);

        assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(buffer));
    }

    @Test
    void freeIgnoresBuffersWithoutDirectMemory() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(64);
        heapBuffer.putInt(0x12345678);

        assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(heapBuffer));
        assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(null));
    }

    @Test
    void freeUsesLegacyCleanerMethodPairWhenConfigured() throws Exception {
        DirectByteBufferDeallocator.free(null);

        StaticBooleanField supported = staticBooleanField("SUPPORTED");
        StaticObjectField<Method> cleaner = staticObjectField("cleaner", Method.class);
        StaticObjectField<Method> cleanerClean = staticObjectField("cleanerClean", Method.class);
        StaticObjectField<Unsafe> deallocatorUnsafe = staticObjectField("UNSAFE", Unsafe.class);
        Method cleanerMethod = Object.class.getMethod("getClass");
        Method cleanMethod = Object.class.getMethod("hashCode");
        ByteBuffer buffer = ByteBuffer.allocateDirect(64);

        try {
            supported.set(true);
            deallocatorUnsafe.set(null);
            cleaner.set(cleanerMethod);
            cleanerClean.set(cleanMethod);

            assertDoesNotThrow(() -> DirectByteBufferDeallocator.free(buffer));
        } finally {
            cleanerClean.restore();
            cleaner.restore();
            deallocatorUnsafe.restore();
            supported.restore();
        }
    }

    private static StaticBooleanField staticBooleanField(String name) throws NoSuchFieldException {
        Field field = DirectByteBufferDeallocator.class.getDeclaredField(name);
        Object base = UNSAFE.staticFieldBase(field);
        long offset = UNSAFE.staticFieldOffset(field);
        return new StaticBooleanField(base, offset, UNSAFE.getBoolean(base, offset));
    }

    private static <T> StaticObjectField<T> staticObjectField(String name, Class<T> type) throws NoSuchFieldException {
        Field field = DirectByteBufferDeallocator.class.getDeclaredField(name);
        Object base = UNSAFE.staticFieldBase(field);
        long offset = UNSAFE.staticFieldOffset(field);
        return new StaticObjectField<>(base, offset, type.cast(UNSAFE.getObject(base, offset)));
    }

    private static Unsafe unsafe() {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            return Unsafe.class.cast(field.get(null));
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static final class StaticBooleanField {
        private final Object base;
        private final long offset;
        private final boolean originalValue;

        private StaticBooleanField(Object base, long offset, boolean originalValue) {
            this.base = base;
            this.offset = offset;
            this.originalValue = originalValue;
        }

        private void set(boolean value) {
            UNSAFE.putBoolean(base, offset, value);
        }

        private void restore() {
            set(originalValue);
        }
    }

    private static final class StaticObjectField<T> {
        private final Object base;
        private final long offset;
        private final T originalValue;

        private StaticObjectField(Object base, long offset, T originalValue) {
            this.base = base;
            this.offset = offset;
            this.originalValue = originalValue;
        }

        private void set(T value) {
            UNSAFE.putObject(base, offset, value);
        }

        private void restore() {
            set(originalValue);
        }
    }
}
