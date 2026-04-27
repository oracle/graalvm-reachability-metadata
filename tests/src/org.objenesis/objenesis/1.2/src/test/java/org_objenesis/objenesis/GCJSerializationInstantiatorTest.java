/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.instantiator.gcj.GCJInstantiatorBase;
import org.objenesis.instantiator.gcj.GCJSerializationInstantiator;

public class GCJSerializationInstantiatorTest {

    @Test
    void delegatesSerializableInstanceCreationToConfiguredGcjNewObjectHook() throws Throwable {
        SerializableTarget preparedInstance = new SerializableTarget();
        NonSerializableParent.constructorCalls.set(0);
        SerializableTarget.constructorCalls.set(0);

        GCJStateAccess.configure(
            gcjNewObjectMethod(),
            new GCJObjectInputStreamSupport(preparedInstance)
        );

        GCJSerializationInstantiator instantiator =
            new GCJSerializationInstantiator(SerializableTarget.class);
        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isSameAs(preparedInstance);
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(0);
        Assertions.assertThat(SerializableTarget.constructorCalls).hasValue(0);
    }

    private static Method gcjNewObjectMethod() throws ReflectiveOperationException {
        MethodHandle newObjectHandle = MethodHandles.lookup().findVirtual(
            GCJObjectInputStreamSupport.class,
            "newObject",
            MethodType.methodType(Object.class, Class.class, Class.class)
        );
        return MethodHandles.reflectAs(Method.class, newObjectHandle);
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
        }
    }

    public static class SerializableTarget extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        public SerializableTarget() {
            constructorCalls.incrementAndGet();
        }
    }

    public static class GCJObjectInputStreamSupport extends ObjectInputStream {
        private final Object preparedInstance;

        public GCJObjectInputStreamSupport(Object preparedInstance) throws IOException {
            super();
            this.preparedInstance = preparedInstance;
        }

        public Object newObject(Class<?> type, Class<?> parentType) {
            if (type != SerializableTarget.class) {
                throw new IllegalArgumentException("Unexpected type: " + type.getName());
            }
            if (parentType != NonSerializableParent.class) {
                throw new IllegalArgumentException(
                    "Unexpected parent type: " + parentType.getName()
                );
            }
            return preparedInstance;
        }
    }

    private abstract static class GCJStateAccess extends GCJInstantiatorBase {
        private GCJStateAccess(Class<?> type) {
            super(type);
        }

        static void configure(Method method, ObjectInputStream stream) {
            newObjectMethod = method;
            dummyStream = stream;
        }
    }
}
