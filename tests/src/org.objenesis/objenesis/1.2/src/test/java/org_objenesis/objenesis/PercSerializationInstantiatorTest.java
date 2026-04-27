/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisException;
import org.objenesis.instantiator.perc.PercSerializationInstantiator;
import org.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

public class PercSerializationInstantiatorTest {

    @Test
    void reportsMissingPercRuntimeClassesOnStandardJvm() throws ReflectiveOperationException {
        resetPercSerializationStaticState();

        Assertions.assertThatThrownBy(
            () -> new PercSerializationInstantiator(SerializableTarget.class)
        )
            .isInstanceOf(ObjenesisException.class)
            .hasCauseInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void delegatesInstanceCreationToConfiguredPercSerializationHook()
        throws ReflectiveOperationException {
        NonSerializableParent.constructorCalls.set(0);
        SerializableTarget.constructorCalls.set(0);

        PercSerializationInstantiator instantiator =
            newUninitializedPercSerializationInstantiator();
        Method noArgConstructMethod = PercRuntimeSupport.class.getDeclaredMethod(
            "noArgConstruct",
            Class.class,
            Object.class,
            PercMethodDescriptor.class
        );
        PercMethodDescriptor methodDescriptor = new PercMethodDescriptor(
            NonSerializableParent.class,
            "<init>()V"
        );

        setField(instantiator, "newInstanceMethod", noArgConstructMethod);
        setField(
            instantiator,
            "typeArgs",
            new Object[] { NonSerializableParent.class, SerializableTarget.class, methodDescriptor }
        );

        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(SerializableTarget.class);
        Assertions.assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        Assertions.assertThat(SerializableTarget.constructorCalls).hasValue(1);
        Assertions.assertThat(((SerializableTarget) instance).parentState)
            .isEqualTo("initialized-by-parent");
        Assertions.assertThat(((SerializableTarget) instance).targetState)
            .isEqualTo("initialized-by-target");
    }

    private static PercSerializationInstantiator newUninitializedPercSerializationInstantiator() {
        SunReflectionFactoryInstantiator instantiator =
            new SunReflectionFactoryInstantiator(PercSerializationInstantiator.class);
        return (PercSerializationInstantiator) instantiator.newInstance();
    }

    private static void setField(Object target, String fieldName, Object value)
        throws ReflectiveOperationException {
        Field field = PercSerializationInstantiator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void resetPercSerializationStaticState() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
            PercSerializationInstantiator.class,
            MethodHandles.lookup()
        );
        clearStaticClassCache(lookup, "class$java$io$Serializable");
    }

    private static void clearStaticClassCache(MethodHandles.Lookup lookup, String fieldName)
        throws ReflectiveOperationException {
        VarHandle field = lookup.findStaticVarHandle(
            PercSerializationInstantiator.class,
            fieldName,
            Class.class
        );
        field.set(null);
    }

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized-by-parent";
        }
    }

    public static class SerializableTarget extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String targetState;

        public SerializableTarget() {
            constructorCalls.incrementAndGet();
            this.targetState = "initialized-by-target";
        }
    }

    public static final class PercMethodDescriptor {

        private final Class<?> ownerType;
        private final String signature;

        private PercMethodDescriptor(Class<?> ownerType, String signature) {
            this.ownerType = ownerType;
            this.signature = signature;
        }
    }

    public static final class PercRuntimeSupport {

        private PercRuntimeSupport() {
        }

        public static Object noArgConstruct(
            Class<?> unserializableType,
            Object type,
            PercMethodDescriptor method
        ) {
            if (unserializableType != NonSerializableParent.class) {
                throw new IllegalArgumentException(
                    "Unexpected unserializable type: " + unserializableType.getName()
                );
            }
            if (type != SerializableTarget.class) {
                throw new IllegalArgumentException("Unexpected type: " + type);
            }
            if (
                method.ownerType != NonSerializableParent.class
                    || !"<init>()V".equals(method.signature)
            ) {
                throw new IllegalArgumentException("Unexpected Perc method description");
            }
            return new SerializableTarget();
        }
    }
}
