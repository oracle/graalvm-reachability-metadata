/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package undertow;

import io.undertow.server.DirectByteBufferDeallocator;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectByteBufferDeallocatorTest {
    private static final String ACTIVE_CLASS_NAME = "io.undertow.server.DirectByteBufferDeallocator";
    private static final String QUEUED_BUFFER_CLASS_NAME = ACTIVE_CLASS_NAME + "$" + "QueuedByteBuffer";

    @Test
    void freeIgnoresNullAndHeapByteBuffers() {
        ByteBuffer heapBuffer = ByteBuffer.allocate(16);

        DirectByteBufferDeallocator.free(null);
        DirectByteBufferDeallocator.free(heapBuffer);

        assertThat(heapBuffer.isDirect()).isFalse();
    }

    @Test
    void freeCleansExpiredDirectByteBufferWhenAnotherBufferIsFreed() throws InterruptedException {
        ByteBuffer firstBuffer = ByteBuffer.allocateDirect(32);
        firstBuffer.putInt(0, 42);

        DirectByteBufferDeallocator.free(firstBuffer);
        Thread.sleep(150L);

        ByteBuffer secondBuffer = ByteBuffer.allocateDirect(32);
        DirectByteBufferDeallocator.free(secondBuffer);

        assertThat(firstBuffer.isDirect()).isTrue();
        assertThat(secondBuffer.isDirect()).isTrue();
    }

    @Test
    void freeCleansDirectByteBufferThroughLegacyCleanerFallback() throws Exception {
        try {
            String originalVersion = System.getProperty("java.specification.version");
            try {
                System.setProperty("java.specification.version", "1.8");
                Class<?> deallocatorClass = Class.forName(ACTIVE_CLASS_NAME, true, new ActiveClassLoader());
                Method clearMethod = ByteBuffer.class.getMethod("clear");
                Unsafe unsafe = getUnsafe();

                writeStaticBooleanField(unsafe, deallocatorClass, "SUPPORTED", true);
                writeStaticObjectField(unsafe, deallocatorClass, "UNSAFE", null);
                writeStaticObjectField(unsafe, deallocatorClass, "cleaner", clearMethod);
                writeStaticObjectField(unsafe, deallocatorClass, "cleanerClean", clearMethod);

                Method freeMethod = deallocatorClass.getMethod("free", ByteBuffer.class);
                ByteBuffer firstBuffer = ByteBuffer.allocateDirect(32);
                firstBuffer.position(7);
                freeMethod.invoke(null, firstBuffer);
                Thread.sleep(150L);

                ByteBuffer secondBuffer = ByteBuffer.allocateDirect(32);
                freeMethod.invoke(null, secondBuffer);

                assertThat(firstBuffer.position()).isZero();
                assertThat(secondBuffer.isDirect()).isTrue();
            } finally {
                if (originalVersion == null) {
                    System.clearProperty("java.specification.version");
                } else {
                    System.setProperty("java.specification.version", originalVersion);
                }
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static void writeStaticObjectField(Unsafe unsafe, Class<?> targetClass, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = targetClass.getDeclaredField(fieldName);
        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static void writeStaticBooleanField(Unsafe unsafe, Class<?> targetClass, String fieldName, boolean value)
            throws ReflectiveOperationException {
        Field field = targetClass.getDeclaredField(fieldName);
        unsafe.putBoolean(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static final class ActiveClassLoader extends ClassLoader {
        private ActiveClassLoader() {
            super(DirectByteBufferDeallocatorTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (ACTIVE_CLASS_NAME.equals(name) || QUEUED_BUFFER_CLASS_NAME.equals(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineActiveClass(name);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
            return super.loadClass(name, resolve);
        }

        private Class<?> defineActiveClass(String className) throws ClassNotFoundException {
            String resourceName = className.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(className);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(className, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(className, exception);
            }
        }
    }
}
