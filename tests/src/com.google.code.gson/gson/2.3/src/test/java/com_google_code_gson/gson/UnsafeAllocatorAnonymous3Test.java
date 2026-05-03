/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.internal.UnsafeAllocator;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class UnsafeAllocatorAnonymous3Test {
    @Test
    void invokesConfiguredLegacyObjectStreamClassAllocationMethod() throws Exception {
        Method fallbackMethod = UnsafeAllocatorAnonymous3Test.class.getDeclaredMethod(
                "legacyObjectStreamClassNewInstance", Class.class, int.class);
        UnsafeAllocator allocator = newLegacyObjectStreamClassAllocator(fallbackMethod, 41);

        FallbackAllocatedMessage message = allocator.newInstance(FallbackAllocatedMessage.class);

        assertThat(message.instantiationClass).isEqualTo(FallbackAllocatedMessage.class);
        assertThat(message.constructorId).isEqualTo(41);
    }

    private static UnsafeAllocator newLegacyObjectStreamClassAllocator(Method fallbackMethod, int constructorId)
            throws Exception {
        Class<?> allocatorClass = Class.forName("com.google.gson.internal.UnsafeAllocator$3");
        Constructor<?> constructor = allocatorClass.getDeclaredConstructor(Method.class, int.class);
        constructor.setAccessible(true);
        return (UnsafeAllocator) constructor.newInstance(fallbackMethod, constructorId);
    }

    public static Object legacyObjectStreamClassNewInstance(Class<?> instantiationClass, int constructorId) {
        return new FallbackAllocatedMessage(instantiationClass, constructorId);
    }

    public static final class FallbackAllocatedMessage {
        private final Class<?> instantiationClass;
        private final int constructorId;

        FallbackAllocatedMessage(Class<?> instantiationClass, int constructorId) {
            this.instantiationClass = instantiationClass;
            this.constructorId = constructorId;
        }
    }
}
