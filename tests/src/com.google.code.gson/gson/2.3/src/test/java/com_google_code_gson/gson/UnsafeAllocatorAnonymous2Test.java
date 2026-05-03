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

public class UnsafeAllocatorAnonymous2Test {
    @Test
    void invokesConfiguredLegacyObjectInputStreamAllocationMethod() throws Exception {
        Method fallbackMethod = UnsafeAllocatorAnonymous2Test.class.getDeclaredMethod(
                "legacyObjectInputStreamNewInstance", Class.class, Class.class);
        UnsafeAllocator allocator = newLegacyObjectInputStreamAllocator(fallbackMethod);

        FallbackAllocatedMessage message = allocator.newInstance(FallbackAllocatedMessage.class);

        assertThat(message.instantiationClass).isEqualTo(FallbackAllocatedMessage.class);
        assertThat(message.constructorClass).isEqualTo(Object.class);
    }

    private static UnsafeAllocator newLegacyObjectInputStreamAllocator(Method fallbackMethod) throws Exception {
        Class<?> allocatorClass = Class.forName("com.google.gson.internal.UnsafeAllocator$2");
        Constructor<?> constructor = allocatorClass.getDeclaredConstructor(Method.class);
        constructor.setAccessible(true);
        return (UnsafeAllocator) constructor.newInstance(fallbackMethod);
    }

    public static Object legacyObjectInputStreamNewInstance(Class<?> instantiationClass, Class<?> constructorClass) {
        return new FallbackAllocatedMessage(instantiationClass, constructorClass);
    }

    public static final class FallbackAllocatedMessage {
        private final Class<?> instantiationClass;
        private final Class<?> constructorClass;

        FallbackAllocatedMessage(Class<?> instantiationClass, Class<?> constructorClass) {
            this.instantiationClass = instantiationClass;
            this.constructorClass = constructorClass;
        }
    }
}
