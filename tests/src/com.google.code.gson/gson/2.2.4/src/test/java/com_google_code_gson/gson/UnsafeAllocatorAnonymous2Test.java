/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.internal.UnsafeAllocator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class UnsafeAllocatorAnonymous2Test {
    @Test
    public void delegatesAllocationToTheCapturedObjectInputStreamMethod() throws Exception {
        AllocationBridge.reset();
        final Method allocationMethod = AllocationBridge.class.getDeclaredMethod(
                "allocate",
                Class.class,
                Class.class
        );
        final UnsafeAllocator allocator = newObjectInputStreamAllocator(allocationMethod);

        final FallbackAllocatedPayload payload = allocator.newInstance(FallbackAllocatedPayload.class);

        assertThat(payload.name).isEqualTo("allocated through fallback");
        assertThat(payload.count).isEqualTo(2);
        assertThat(AllocationBridge.requestedType).isSameAs(FallbackAllocatedPayload.class);
        assertThat(AllocationBridge.requestedConstructorType).isSameAs(Object.class);
    }

    private static UnsafeAllocator newObjectInputStreamAllocator(final Method allocationMethod) throws Exception {
        final Class<? extends UnsafeAllocator> allocatorClass = Class
                .forName("com.google.gson.internal.UnsafeAllocator$2")
                .asSubclass(UnsafeAllocator.class);
        final Constructor<? extends UnsafeAllocator> constructor = allocatorClass.getDeclaredConstructor(Method.class);
        constructor.setAccessible(true);
        return constructor.newInstance(allocationMethod);
    }

    public static final class AllocationBridge {
        static Class<?> requestedType;
        static Class<?> requestedConstructorType;

        private AllocationBridge() {
        }

        public static Object allocate(final Class<?> type, final Class<?> constructorType) {
            requestedType = type;
            requestedConstructorType = constructorType;
            return new FallbackAllocatedPayload("allocated through fallback", 2);
        }

        static void reset() {
            requestedType = null;
            requestedConstructorType = null;
        }
    }

    public static final class FallbackAllocatedPayload {
        final String name;
        final int count;

        private FallbackAllocatedPayload(final String name, final int count) {
            this.name = name;
            this.count = count;
        }
    }
}
