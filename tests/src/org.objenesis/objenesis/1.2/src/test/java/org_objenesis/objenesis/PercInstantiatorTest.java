/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_objenesis.objenesis;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisException;
import org.objenesis.instantiator.perc.PercInstantiator;
import org.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;

public class PercInstantiatorTest {

    @Test
    void reportsMissingPercObjectInputStreamHookOnStandardJvm()
        throws ReflectiveOperationException {
        resetPercStaticState();

        Assertions.assertThatThrownBy(() -> new PercInstantiator(ConstructorTarget.class))
            .isInstanceOf(ObjenesisException.class)
            .hasCauseInstanceOf(NoSuchMethodException.class);
    }

    @Test
    void delegatesInstanceCreationToConfiguredPercHook() throws ReflectiveOperationException {
        ConstructorTarget.constructorCalls.set(0);
        PercInstantiator instantiator = newUninitializedPercInstantiator();
        Method newInstanceMethod = PercRuntimeSupport.class.getDeclaredMethod(
            "newInstance",
            Class.class,
            Boolean.TYPE
        );

        setField(instantiator, "newInstanceMethod", newInstanceMethod);
        setField(instantiator, "typeArgs", new Object[] { ConstructorTarget.class, Boolean.FALSE });

        Object instance = instantiator.newInstance();

        Assertions.assertThat(instance).isInstanceOf(ConstructorTarget.class);
        Assertions.assertThat(ConstructorTarget.constructorCalls).hasValue(1);
    }

    private static PercInstantiator newUninitializedPercInstantiator() {
        SunReflectionFactoryInstantiator instantiator =
            new SunReflectionFactoryInstantiator(PercInstantiator.class);
        return (PercInstantiator) instantiator.newInstance();
    }

    private static void setField(Object target, String fieldName, Object value)
        throws ReflectiveOperationException {
        Field field = PercInstantiator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void resetPercStaticState() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup =
            MethodHandles.privateLookupIn(PercInstantiator.class, MethodHandles.lookup());
        clearStaticClassCache(lookup, "class$java$io$ObjectInputStream");
        clearStaticClassCache(lookup, "class$java$lang$Class");
    }

    private static void clearStaticClassCache(MethodHandles.Lookup lookup, String fieldName)
        throws ReflectiveOperationException {
        VarHandle field = lookup.findStaticVarHandle(
            PercInstantiator.class,
            fieldName,
            Class.class
        );
        field.set(null);
    }

    public static class ConstructorTarget {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        public ConstructorTarget() {
            constructorCalls.incrementAndGet();
        }
    }

    public static final class PercRuntimeSupport {

        private PercRuntimeSupport() {
        }

        public static Object newInstance(Class<?> type, boolean useConstructor) {
            if (type != ConstructorTarget.class) {
                throw new IllegalArgumentException("Unexpected type: " + type.getName());
            }
            if (useConstructor) {
                throw new IllegalArgumentException("Expected constructor bypass request");
            }
            return new ConstructorTarget();
        }
    }
}
