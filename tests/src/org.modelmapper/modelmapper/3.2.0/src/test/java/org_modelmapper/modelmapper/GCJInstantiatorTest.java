/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.gcj.GCJInstantiator;
import org.modelmapper.internal.objenesis.instantiator.gcj.GCJInstantiatorBase;

import sun.misc.Unsafe;

public class GCJInstantiatorTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void invokesConfiguredGcjObjectCreationMethod() throws Exception {
        ConstructorBypassedType.constructorCalls.set(0);

        GCJInstantiator<ConstructorBypassedType> instantiator =
            gcjInstantiator(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(ConstructorBypassedType.class);
        assertThat(instance.state).isNull();
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    private static <T> GCJInstantiator<T> gcjInstantiator(Class<T> type) throws Exception {
        @SuppressWarnings("unchecked")
        GCJInstantiator<T> instantiator =
            (GCJInstantiator<T>) UNSAFE.allocateInstance(GCJInstantiator.class);
        putInstanceField(instantiator, "type", type);
        putStaticField("newObjectMethod", GCJObjectInputStreamSupport.newObjectMethod());
        putStaticField("dummyStream", null);
        return instantiator;
    }

    private static void putInstanceField(GCJInstantiator<?> instantiator, String fieldName, Object value)
        throws Exception {
        Field field = GCJInstantiatorBase.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        UNSAFE.putObject(instantiator, UNSAFE.objectFieldOffset(field), value);
    }

    private static void putStaticField(String fieldName, Object value) throws Exception {
        Field field = GCJInstantiatorBase.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        UNSAFE.putObject(UNSAFE.staticFieldBase(field), UNSAFE.staticFieldOffset(field), value);
    }

    private static Unsafe unsafe() {
        try {
            Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
            unsafe.setAccessible(true);
            return (Unsafe) unsafe.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    public static class ConstructorBypassedType {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String state;

        public ConstructorBypassedType() {
            constructorCalls.incrementAndGet();
            this.state = "initialized-by-constructor";
        }
    }

    public static final class GCJObjectInputStreamSupport {
        private GCJObjectInputStreamSupport() {
        }

        public static Method newObjectMethod() throws NoSuchMethodException {
            return GCJObjectInputStreamSupport.class.getMethod("newObject", Class.class, Class.class);
        }

        public static Object newObject(Class<?> instantiableType, Class<?> parentType) throws InstantiationException {
            if (instantiableType != ConstructorBypassedType.class) {
                throw new IllegalArgumentException("Unexpected type: " + instantiableType.getName());
            }
            if (parentType != Object.class) {
                throw new IllegalArgumentException("Unexpected parent type: " + parentType.getName());
            }
            return UNSAFE.allocateInstance(instantiableType);
        }
    }
}
