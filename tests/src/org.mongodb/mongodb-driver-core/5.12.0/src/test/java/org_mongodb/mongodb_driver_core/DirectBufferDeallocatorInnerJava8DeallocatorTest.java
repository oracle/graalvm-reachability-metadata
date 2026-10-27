/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongodb_driver_core;

import com.mongodb.internal.connection.tlschannel.util.DirectBufferDeallocator;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

public class DirectBufferDeallocatorInnerJava8DeallocatorTest {
    @Test
    void deallocateInvokesCleanerThroughJava8Deallocator() throws Exception {
        Java8DeallocatorState.reset();
        final Unsafe unsafe = unsafe();
        final Object java8Deallocator = unsafe.allocateInstance(java8DeallocatorClass());
        setObjectField(
                unsafe, java8Deallocator, "cleanerAccessor", accessibleMethod(Java8CleanerAccessor.class, "cleaner"));
        setObjectField(unsafe, java8Deallocator, "clean", accessibleMethod(Java8Cleaner.class, "clean"));
        final DirectBufferDeallocator deallocator =
                (DirectBufferDeallocator) unsafe.allocateInstance(DirectBufferDeallocator.class);
        setObjectField(unsafe, deallocator, "deallocator", java8Deallocator);
        final ByteBuffer directBuffer = ByteBuffer.allocateDirect(Byte.BYTES);

        deallocator.deallocate(directBuffer);

        assertThat(Java8DeallocatorState.cleaned()).isTrue();
    }

    private static Class<?> java8DeallocatorClass() {
        for (Class<?> nestedClass : DirectBufferDeallocator.class.getDeclaredClasses()) {
            if ("Java8Deallocator".equals(nestedClass.getSimpleName())) {
                return nestedClass;
            }
        }
        throw new AssertionError("Java8Deallocator nested class not found");
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        final Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        return (Unsafe) theUnsafe.get(null);
    }

    private static Method accessibleMethod(final Class<?> declaringClass, final String methodName)
            throws ReflectiveOperationException {
        final Method method = declaringClass.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method;
    }

    private static void setObjectField(
            final Unsafe unsafe, final Object target, final String fieldName, final Object value)
            throws ReflectiveOperationException {
        final Field field = target.getClass().getDeclaredField(fieldName);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }
}

final class Java8CleanerAccessor {
    static Object cleaner() {
        return new Java8Cleaner();
    }
}

final class Java8Cleaner {
    void clean() {
        Java8DeallocatorState.markCleaned();
    }
}

final class Java8DeallocatorState {
    private static final AtomicBoolean CLEANED = new AtomicBoolean();

    private Java8DeallocatorState() {
    }

    static void reset() {
        CLEANED.set(false);
    }

    static void markCleaned() {
        CLEANED.set(true);
    }

    static boolean cleaned() {
        return CLEANED.get();
    }
}
