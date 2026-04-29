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
import org.modelmapper.internal.objenesis.instantiator.android.Android17Instantiator;

import sun.misc.Unsafe;

public class Android17InstantiatorTest {
    private static final int OBJECT_CONSTRUCTOR_ID = 17;
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void invokesAndroidObjectStreamClassCreationHook() throws Exception {
        ConstructorBypassedType.constructorCalls.set(0);

        Android17Instantiator<ConstructorBypassedType> instantiator =
            android17Instantiator(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(ConstructorBypassedType.class);
        assertThat(instance.state).isNull();
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    private static <T> Android17Instantiator<T> android17Instantiator(Class<T> type) throws Exception {
        @SuppressWarnings("unchecked")
        Android17Instantiator<T> instantiator =
            (Android17Instantiator<T>) UNSAFE.allocateInstance(Android17Instantiator.class);
        putField(instantiator, "type", type);
        putField(instantiator, "newInstanceMethod", AndroidObjectStreamClassSupport.newInstanceMethod());
        putField(instantiator, "objectConstructorId", OBJECT_CONSTRUCTOR_ID);
        return instantiator;
    }

    private static void putField(Android17Instantiator<?> instantiator, String fieldName, Object value)
        throws Exception {
        Field field = Android17Instantiator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        UNSAFE.putObject(instantiator, UNSAFE.objectFieldOffset(field), value);
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

    public static final class AndroidObjectStreamClassSupport {
        private AndroidObjectStreamClassSupport() {
        }

        public static Method newInstanceMethod() throws NoSuchMethodException {
            return AndroidObjectStreamClassSupport.class.getMethod("newInstance", Class.class, int.class);
        }

        public static Object newInstance(Class<?> instantiableType, int constructorId) throws InstantiationException {
            if (instantiableType != ConstructorBypassedType.class) {
                throw new IllegalArgumentException("Unexpected type: " + instantiableType.getName());
            }
            if (constructorId != OBJECT_CONSTRUCTOR_ID) {
                throw new IllegalArgumentException("Unexpected constructor id: " + constructorId);
            }
            return UNSAFE.allocateInstance(instantiableType);
        }
    }
}
