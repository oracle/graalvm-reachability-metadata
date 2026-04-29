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
import org.modelmapper.internal.objenesis.instantiator.perc.PercInstantiator;

import sun.misc.Unsafe;

public class PercInstantiatorTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void invokesConfiguredPercObjectCreationMethod() throws Exception {
        ConstructorBypassedType.constructorCalls.set(0);

        PercInstantiator<ConstructorBypassedType> instantiator =
            percInstantiator(ConstructorBypassedType.class);
        ConstructorBypassedType instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(ConstructorBypassedType.class);
        assertThat(instance.state).isNull();
        assertThat(ConstructorBypassedType.constructorCalls).hasValue(0);
    }

    private static <T> PercInstantiator<T> percInstantiator(Class<T> type) throws Exception {
        @SuppressWarnings("unchecked")
        PercInstantiator<T> instantiator =
            (PercInstantiator<T>) UNSAFE.allocateInstance(PercInstantiator.class);
        putInstanceField(instantiator, "newInstanceMethod", PercObjectInputStreamSupport.newInstanceMethod());
        putInstanceField(instantiator, "typeArgs", new Object[] {type, Boolean.FALSE});
        return instantiator;
    }

    private static void putInstanceField(PercInstantiator<?> instantiator, String fieldName, Object value)
        throws Exception {
        Field field = PercInstantiator.class.getDeclaredField(fieldName);
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

    public static final class PercObjectInputStreamSupport {
        private PercObjectInputStreamSupport() {
        }

        public static Method newInstanceMethod() throws NoSuchMethodException {
            return PercObjectInputStreamSupport.class.getMethod("newInstance", Class.class, boolean.class);
        }

        public static Object newInstance(Class<?> type, boolean useConstructor) throws InstantiationException {
            if (type != ConstructorBypassedType.class) {
                throw new IllegalArgumentException("Unexpected type: " + type.getName());
            }
            if (useConstructor) {
                throw new IllegalArgumentException("Expected constructor bypass request");
            }
            return UNSAFE.allocateInstance(type);
        }
    }
}
