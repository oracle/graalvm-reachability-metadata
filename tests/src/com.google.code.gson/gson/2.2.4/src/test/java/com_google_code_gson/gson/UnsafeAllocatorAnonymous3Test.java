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

public class UnsafeAllocatorAnonymous3Test {
    @Test
    public void delegatesAllocationToCapturedObjectStreamClassMethod() throws Exception {
        AllocationBridge.reset();
        final Method allocationMethod = AllocationBridge.class.getDeclaredMethod(
                "allocate",
                Class.class,
                int.class
        );
        final int constructorId = 37;
        final UnsafeAllocator allocator = newObjectStreamClassAllocator(allocationMethod, constructorId);

        final ObjectStreamAllocatedPayload payload = allocator.newInstance(ObjectStreamAllocatedPayload.class);

        assertThat(payload.name).isEqualTo("allocated through object stream class");
        assertThat(payload.count).isEqualTo(3);
        assertThat(AllocationBridge.requestedType).isSameAs(ObjectStreamAllocatedPayload.class);
        assertThat(AllocationBridge.requestedConstructorId).isEqualTo(constructorId);
    }

    private static UnsafeAllocator newObjectStreamClassAllocator(
            final Method allocationMethod,
            final int constructorId
    ) throws Exception {
        final Class<? extends UnsafeAllocator> allocatorClass = Class
                .forName("com.google.gson.internal.UnsafeAllocator$3")
                .asSubclass(UnsafeAllocator.class);
        final Constructor<? extends UnsafeAllocator> constructor = allocatorClass.getDeclaredConstructor(
                Method.class,
                int.class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(allocationMethod, constructorId);
    }

    public static final class AllocationBridge {
        static Class<?> requestedType;
        static int requestedConstructorId;

        private AllocationBridge() {
        }

        public static Object allocate(final Class<?> type, final int constructorId) {
            requestedType = type;
            requestedConstructorId = constructorId;
            return new ObjectStreamAllocatedPayload("allocated through object stream class", 3);
        }

        static void reset() {
            requestedType = null;
            requestedConstructorId = -1;
        }
    }

    public static final class ObjectStreamAllocatedPayload {
        final String name;
        final int count;

        private ObjectStreamAllocatedPayload(final String name, final int count) {
            this.name = name;
            this.count = count;
        }
    }
}
