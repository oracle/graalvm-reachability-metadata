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
import org.modelmapper.internal.objenesis.instantiator.perc.PercSerializationInstantiator;

import sun.misc.Unsafe;

public class PercSerializationInstantiatorTest {
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void invokesConfiguredPercSerializationConstructionMethod() throws Exception {
        NonSerializableParent.constructorCalls.set(0);
        SerializableChild.constructorCalls.set(0);

        PercSerializationInstantiator<SerializableChild> instantiator =
            percSerializationInstantiator(SerializableChild.class, NonSerializableParent.class);
        SerializableChild instance = instantiator.newInstance();

        assertThat(instance).isNotNull();
        assertThat(instance).isExactlyInstanceOf(SerializableChild.class);
        assertThat(NonSerializableParent.constructorCalls).hasValue(1);
        assertThat(SerializableChild.constructorCalls).hasValue(0);
        assertThat(instance.parentState).isEqualTo("initialized-by-parent");
        assertThat(instance.childState).isNull();
    }

    private static <T> PercSerializationInstantiator<T> percSerializationInstantiator(
        Class<T> type,
        Class<? super T> unserializableType) throws Exception {
        @SuppressWarnings("unchecked")
        PercSerializationInstantiator<T> instantiator =
            (PercSerializationInstantiator<T>) UNSAFE.allocateInstance(PercSerializationInstantiator.class);
        putInstanceField(instantiator, "newInstanceMethod", PercObjectInputStreamSupport.noArgConstructMethod());
        putInstanceField(
            instantiator,
            "typeArgs",
            new Object[] {unserializableType, type, new PercMethodSupport(unserializableType, "<init>()V")}
        );
        return instantiator;
    }

    private static void putInstanceField(
        PercSerializationInstantiator<?> instantiator,
        String fieldName,
        Object value) throws Exception {
        Field field = PercSerializationInstantiator.class.getDeclaredField(fieldName);
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

        transient String childState;

        public SerializableChild() {
            constructorCalls.incrementAndGet();
            this.childState = "initialized-by-child";
        }
    }

    public static final class PercMethodSupport {
        private final Class<?> ownerType;
        private final String signature;

        private PercMethodSupport(Class<?> ownerType, String signature) {
            this.ownerType = ownerType;
            this.signature = signature;
        }
    }

    public static final class PercObjectInputStreamSupport {
        private PercObjectInputStreamSupport() {
        }

        public static Method noArgConstructMethod() throws NoSuchMethodException {
            return PercObjectInputStreamSupport.class.getMethod(
                "noArgConstruct",
                Class.class,
                Object.class,
                PercMethodSupport.class
            );
        }

        public static Object noArgConstruct(
            Class<?> unserializableType,
            Object type,
            PercMethodSupport method) {
            if (unserializableType != NonSerializableParent.class) {
                throw new IllegalArgumentException(
                    "Unexpected unserializable type: " + unserializableType.getName()
                );
            }
            if (type != SerializableChild.class) {
                throw new IllegalArgumentException("Unexpected type: " + type);
            }
            if (
                method.ownerType != NonSerializableParent.class
                    || !"<init>()V".equals(method.signature)
            ) {
                throw new IllegalArgumentException("Unexpected Perc method description");
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
