/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.gcj.GCJInstantiatorBase;
import org.modelmapper.internal.objenesis.instantiator.gcj.GCJSerializationInstantiator;

import sun.misc.Unsafe;

public class GCJSerializationInstantiatorTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void invokesConfiguredGcjObjectCreationMethodWithSerializableSuperType() throws Exception {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        GCJSerializationInstantiator<SerializableChild> instantiator =
            gcjSerializationInstantiator(SerializableChild.class, NonSerializableParent.class);
        SerializableChild instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(SerializableChild.class);
        assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        assertThat(SerializableChild.constructorCalls).hasValue(0);
        assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        assertThat(instance.childState).isNull();
    }

    private static <T> GCJSerializationInstantiator<T> gcjSerializationInstantiator(
        Class<T> type,
        Class<? super T> superType) throws Exception {
        @SuppressWarnings("unchecked")
        GCJSerializationInstantiator<T> instantiator =
            (GCJSerializationInstantiator<T>) UNSAFE.allocateInstance(GCJSerializationInstantiator.class);
        putInstanceField(instantiator, GCJInstantiatorBase.class, "type", type);
        putInstanceField(instantiator, GCJSerializationInstantiator.class, "superType", superType);
        putStaticField("newObjectMethod", GCJObjectInputStreamSupport.newObjectMethod());
        putStaticField("dummyStream", null);
        return instantiator;
    }

    private static void putInstanceField(
        GCJSerializationInstantiator<?> instantiator,
        Class<?> declaringType,
        String fieldName,
        Object value) throws Exception {
        Field field = declaringType.getDeclaredField(fieldName);
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

    public static class NonSerializableParent {
        static final AtomicInteger constructorCalls = new AtomicInteger();

        String parentState;

        public NonSerializableParent() {
            constructorCalls.incrementAndGet();
            this.parentState = "initialized-by-parent";
        }
    }

    public static class SerializableChild extends NonSerializableParent implements Serializable {
        private static final long serialVersionUID = 1L;

        static final AtomicInteger constructorCalls = new AtomicInteger();

        String childState;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "initialized-by-child";
        }
    }

    public static final class GCJObjectInputStreamSupport {
        private GCJObjectInputStreamSupport() {
        }

        public static Method newObjectMethod() throws NoSuchMethodException {
            return GCJObjectInputStreamSupport.class.getMethod("newObject", Class.class, Class.class);
        }

        public static Object newObject(Class<?> instantiableType, Class<?> parentType) {
            if (instantiableType != SerializableChild.class) {
                throw new IllegalArgumentException("Unexpected type: " + instantiableType.getName());
            }
            if (parentType != NonSerializableParent.class) {
                throw new IllegalArgumentException("Unexpected parent type: " + parentType.getName());
            }

            SerializableChild template = new SerializableChild();
            NonSerializableParent.constructorCalls.set(0);
            SerializableChild.constructorCalls.set(0);
            return deserialize(serialize(template));
        }

        private static byte[] serialize(Serializable value) {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(outputStream)) {
                    objectOutputStream.writeObject(value);
                }
                return outputStream.toByteArray();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        private static Object deserialize(byte[] serializedValue) {
            try (ObjectInputStream objectInputStream =
                new ObjectInputStream(new ByteArrayInputStream(serializedValue))) {
                return objectInputStream.readObject();
            } catch (IOException | ClassNotFoundException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
