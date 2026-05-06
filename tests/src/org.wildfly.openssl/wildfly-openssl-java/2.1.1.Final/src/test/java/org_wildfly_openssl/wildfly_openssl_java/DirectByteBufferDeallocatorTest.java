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

import static org.assertj.core.api.Assertions.assertThat;

public class DirectByteBufferDeallocatorTest {
    @Test
    void freeReleasesDirectByteBufferThroughPublicApi() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(32);
        buffer.putInt(0, 42);

        DirectByteBufferDeallocator.free(buffer);

        assertThat(buffer.isDirect()).isTrue();
    }

    @Test
    void freeIgnoresNullAndHeapByteBuffers() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(16);

        DirectByteBufferDeallocator.free(null);
        DirectByteBufferDeallocator.free(heapBuffer);

        assertThat(heapBuffer.isDirect()).isFalse();
    }

    @Test
    void freeCanUseCleanerMethodFallback() throws ReflectiveOperationException {
        Unsafe unsafe = getUnsafe();
        Field unsafeField = getDirectByteBufferDeallocatorField("UNSAFE");
        Field cleanerField = getDirectByteBufferDeallocatorField("cleaner");
        Field cleanerCleanField = getDirectByteBufferDeallocatorField("cleanerClean");
        Object originalUnsafe = readStaticField(unsafe, unsafeField);
        Object originalCleaner = readStaticField(unsafe, cleanerField);
        Object originalCleanerClean = readStaticField(unsafe, cleanerCleanField);
        Method clear = ByteBuffer.class.getMethod("clear");

        try {
            writeStaticField(unsafe, unsafeField, null);
            writeStaticField(unsafe, cleanerField, clear);
            writeStaticField(unsafe, cleanerCleanField, clear);
            assertThat(readStaticField(unsafe, unsafeField)).isNull();

            ByteBuffer buffer = ByteBuffer.allocateDirect(32);

            DirectByteBufferDeallocator.free(buffer);

            assertThat(buffer.isDirect()).isTrue();
        } finally {
            writeStaticField(unsafe, unsafeField, originalUnsafe);
            writeStaticField(unsafe, cleanerField, originalCleaner);
            writeStaticField(unsafe, cleanerCleanField, originalCleanerClean);
        }
    }

    private static Field getDirectByteBufferDeallocatorField(String fieldName) throws NoSuchFieldException {
        Field field = DirectByteBufferDeallocator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static Object readStaticField(Unsafe unsafe, Field field) {
        return unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
    }

    private static void writeStaticField(Unsafe unsafe, Field field, Object value) {
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }
}
