/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PureJavaReflectionProviderTest {
    @Test
    @Order(1)
    void createsObjectsWithNoArgumentConstructors() {
        PureJavaReflectionProvider provider = new PureJavaReflectionProvider();

        Object created = provider.newInstance(ConstructorCreatedValue.class);

        assertThat(created).isExactlyInstanceOf(ConstructorCreatedValue.class);
        assertThat(((ConstructorCreatedValue)created).value).isEqualTo("created by constructor");
    }

    @Test
    @Order(2)
    void createsSerializableObjectsWithObjectStreamClassNewInstance() {
        PureJavaReflectionProvider provider = new PureJavaReflectionProvider();
        SerializationCreatedValue.constructorCalls.set(0);

        Object created = provider.newInstance(SerializationCreatedValue.class);

        assertSerializableValueCreatedWithoutConstructor(created);
    }

    @Test
    @Order(3)
    void createsSerializableObjectsWithObjectInputStreamFallback() throws Exception {
        forceObjectStreamClassNewInstanceUnavailable();
        PureJavaReflectionProvider provider = new PureJavaReflectionProvider();
        SerializationCreatedValue.constructorCalls.set(0);

        Object created = provider.newInstance(SerializationCreatedValue.class);

        assertSerializableValueCreatedWithoutConstructor(created);
    }

    private static void assertSerializableValueCreatedWithoutConstructor(Object created) {
        assertThat(created).isExactlyInstanceOf(SerializationCreatedValue.class);
        assertThat(SerializationCreatedValue.constructorCalls).hasValue(0);
        assertThat(((SerializationCreatedValue)created).value).isNull();
    }

    private static void forceObjectStreamClassNewInstanceUnavailable() throws Exception {
        Class<?> reflections = Class.forName(PureJavaReflectionProvider.class.getName() + "$Reflections");
        Field newInstance = reflections.getDeclaredField("newInstance");
        Unsafe unsafe = unsafe();
        Object base = unsafe.staticFieldBase(newInstance);
        long offset = unsafe.staticFieldOffset(newInstance);
        unsafe.putObjectVolatile(base, offset, null);
        assertThat(unsafe.getObjectVolatile(base, offset)).isNull();
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe)unsafeField.get(null);
    }

    public static final class ConstructorCreatedValue {
        private final String value;

        private ConstructorCreatedValue() {
            value = "created by constructor";
        }
    }

    public static final class SerializationCreatedValue implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final AtomicInteger constructorCalls = new AtomicInteger();

        private final String value;

        public SerializationCreatedValue(String value) {
            constructorCalls.incrementAndGet();
            this.value = value;
        }
    }
}
