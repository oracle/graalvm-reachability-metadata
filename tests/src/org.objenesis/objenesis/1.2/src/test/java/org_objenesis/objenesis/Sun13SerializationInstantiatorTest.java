/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.sun.Sun13SerializationInstantiator;

public class Sun13SerializationInstantiatorTest {

    @Test
    void delegatesSerializableInstanceCreationToConfiguredSun13AllocationHook()
        throws ReflectiveOperationException {
        SerializableChild preparedInstance = new SerializableChild();
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);
        Sun13SerializationRuntimeSupport.prepare(preparedInstance);
        Method allocateNewObjectMethod = Sun13SerializationRuntimeSupport.class.getMethod(
            "allocateNewObject",
            Class.class,
            Class.class
        );
        TestSun13SerializationInstantiator.configure(allocateNewObjectMethod);

        Sun13SerializationInstantiator instantiator =
            new Sun13SerializationInstantiator(SerializableChild.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isSameAs(preparedInstance);
        Assertions.assertThat(Sun13SerializationRuntimeSupport.requestedType())
            .isEqualTo(SerializableChild.class);
        Assertions.assertThat(Sun13SerializationRuntimeSupport.requestedParentType())
            .isEqualTo(NonSerializableParent.class);
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(0);
        Assertions.assertThat(SerializableChild.constructorCalls).hasValue(0);
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;
        static final AtomicInteger constructorCalls = new AtomicInteger();

        public SerializableChild() {
            constructorCalls.incrementAndGet();
        }
    }

    public static final class Sun13SerializationRuntimeSupport {
        private static Object preparedInstance;
        private static Class<?> requestedType;
        private static Class<?> requestedParentType;

        private Sun13SerializationRuntimeSupport() {
        }

        static void prepare(Object instance) {
            preparedInstance = instance;
            requestedType = null;
            requestedParentType = null;
        }

        public static Object allocateNewObject(Class<?> type, Class<?> parentType) {
            requestedType = type;
            requestedParentType = parentType;
            return preparedInstance;
        }

        static Class<?> requestedType() {
            return requestedType;
        }

        static Class<?> requestedParentType() {
            return requestedParentType;
        }
    }

    private static final class TestSun13SerializationInstantiator
        extends Sun13SerializationInstantiator {

        private TestSun13SerializationInstantiator(Class<?> type) {
            super(type);
        }

        static void configure(Method method) {
            allocateNewObjectMethod = method;
        }
    }
}
