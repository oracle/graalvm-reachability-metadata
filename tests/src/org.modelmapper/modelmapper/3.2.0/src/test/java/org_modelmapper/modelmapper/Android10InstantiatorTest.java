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
import org.modelmapper.internal.objenesis.instantiator.android.Android10Instantiator;

import sun.misc.Unsafe;

public class Android10InstantiatorTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void invokesAndroidObjectInputStreamCreationHook() throws Exception {
        ConstructorBypassedType.constructorCalls.set(0);

        Android10Instantiator<ConstructorBypassedType> instantiator =
            android10Instantiator(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(ConstructorBypassedType.class);
        assertThat(instance.state).isNull();
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    private static <T> Android10Instantiator<T> android10Instantiator(Class<T> type) throws Exception {
        @SuppressWarnings("unchecked")
        Android10Instantiator<T> instantiator =
            (Android10Instantiator<T>) UNSAFE.allocateInstance(Android10Instantiator.class);
        putField(instantiator, "type", type);
        putField(instantiator, "newStaticMethod", AndroidObjectInputStreamSupport.newInstanceMethod());
        return instantiator;
    }

    private static void putField(Android10Instantiator<?> instantiator, String fieldName, Object value)
        throws Exception {
        Field field = Android10Instantiator.class.getDeclaredField(fieldName);
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

    public static final class AndroidObjectInputStreamSupport {
        private AndroidObjectInputStreamSupport() {
        }

        public static Method newInstanceMethod() throws NoSuchMethodException {
            return AndroidObjectInputStreamSupport.class.getMethod("newInstance", Class.class, Class.class);
        }

        public static Object newInstance(Class<?> instantiableType, Class<?> parentType) throws InstantiationException {
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
