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
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.MethodAccessor;
import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.android.AndroidSerializationInstantiator;

public class AndroidSerializationInstantiatorTest {
    private static final String NEW_INSTANCE_METHOD = "newInstance";
    private static final Class<?>[] ANDROID_NEW_INSTANCE_PARAMETERS = {Class.class};
    private static final Unsafe UNSAFE = unsafe();

    @Test
    void createsSerializableInstancesUsingAndroidSerializationConstructionRules() throws Exception {
        ObjectStreamClassNewInstancePatch patch = ObjectStreamClassNewInstancePatch.install();
        try {
            NonSerializableParent.constructorCalls.set(0);
            SerializableChild.constructorCalls.set(0);

            AndroidSerializationInstantiator<SerializableChild> instantiator =
                new AndroidSerializationInstantiator<>(SerializableChild.class);
            SerializableChild instance = instantiator.newInstance();

            assertThat(instance).isNotNull();
            assertThat(NonSerializableParent.constructorCalls).hasValue(1);
            assertThat(SerializableChild.constructorCalls).hasValue(0);
            assertThat(instance.parentState).isEqualTo("initialized-by-parent");
            assertThat(instance.childState).isNull();
        } finally {
            patch.close();
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

    private static final class ObjectStreamClassNewInstancePatch implements AutoCloseable {
        private static final String PARAMETER_TYPES_FIELD = "parameterTypes";
        private static final String METHOD_ACCESSOR_FIELD = "methodAccessor";

        private final Method method;
        private final Object parameterTypes;
        private final Object methodAccessor;

        private ObjectStreamClassNewInstancePatch(Method method, Object parameterTypes, Object methodAccessor) {
            this.method = method;
            this.parameterTypes = parameterTypes;
            this.methodAccessor = methodAccessor;
        }

        static ObjectStreamClassNewInstancePatch install() throws Exception {
            Method method = objectStreamClassNewInstanceRootMethod();
            Object previousParameterTypes = getMethodField(method, PARAMETER_TYPES_FIELD);
            Object previousMethodAccessor = getMethodField(method, METHOD_ACCESSOR_FIELD);

            putMethodField(method, PARAMETER_TYPES_FIELD, ANDROID_NEW_INSTANCE_PARAMETERS);
            putMethodField(method, METHOD_ACCESSOR_FIELD, new AndroidNewInstanceMethodAccessor());
            return new ObjectStreamClassNewInstancePatch(method, previousParameterTypes, previousMethodAccessor);
        }

        @Override
        public void close() {
            putMethodField(method, PARAMETER_TYPES_FIELD, parameterTypes);
            putMethodField(method, METHOD_ACCESSOR_FIELD, methodAccessor);
        }

        private static Method objectStreamClassNewInstanceRootMethod() throws Exception {
            ObjectStreamClass.class.getDeclaredMethods();
            Object reflectionData = reflectionData(ObjectStreamClass.class);
            Field declaredMethodsField = reflectionData.getClass().getDeclaredField("declaredMethods");
            declaredMethodsField.setAccessible(true);
            Method[] declaredMethods = (Method[]) declaredMethodsField.get(reflectionData);

            for (Method method : declaredMethods) {
                if (NEW_INSTANCE_METHOD.equals(method.getName()) && method.getParameterCount() == 0) {
                    return method;
                }
            }
            throw new NoSuchMethodException(ObjectStreamClass.class.getName() + "." + NEW_INSTANCE_METHOD + "()");
        }

        private static Object reflectionData(Class<?> type) throws Exception {
            Method reflectionDataMethod = Class.class.getDeclaredMethod("reflectionData");
            reflectionDataMethod.setAccessible(true);
            return reflectionDataMethod.invoke(type);
        }
    }

    private static final class AndroidNewInstanceMethodAccessor implements MethodAccessor {
        @Override
        public Object invoke(Object target, Object[] arguments)
            throws IllegalArgumentException, InvocationTargetException {
            return invoke(target, arguments, null);
        }

        @Override
        public Object invoke(Object target, Object[] arguments, Class<?> caller)
            throws IllegalArgumentException, InvocationTargetException {
            if (!(target instanceof ObjectStreamClass)) {
                throw new IllegalArgumentException("Unexpected ObjectStreamClass receiver: " + target);
            }
            if (arguments == null || arguments.length != 1 || arguments[0] != SerializableChild.class) {
                throw new IllegalArgumentException("Unexpected Android serialization target");
            }
            try {
                SerializableChild template = new SerializableChild();
                NonSerializableParent.constructorCalls.set(0);
                SerializableChild.constructorCalls.set(0);
                return deserialize(serialize(template));
            } catch (RuntimeException exception) {
                throw new InvocationTargetException(exception);
            }
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

    private static Object getMethodField(Method method, String fieldName) {
        return UNSAFE.getReference(method, methodFieldOffset(fieldName));
    }

    private static void putMethodField(Method method, String fieldName, Object value) {
        UNSAFE.putReference(method, methodFieldOffset(fieldName), value);
    }

    private static long methodFieldOffset(String fieldName) {
        return UNSAFE.objectFieldOffset(Method.class, fieldName);
    }

    private static Unsafe unsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(null);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
