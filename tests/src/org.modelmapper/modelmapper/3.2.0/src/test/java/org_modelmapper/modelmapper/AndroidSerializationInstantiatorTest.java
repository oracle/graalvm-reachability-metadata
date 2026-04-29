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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.objenesis.instantiator.android.AndroidSerializationInstantiator;

public class AndroidSerializationInstantiatorTest {
    private static final String NEW_INSTANCE_METHOD = "newInstance";
    private static final Class<?>[] ANDROID_NEW_INSTANCE_PARAMETERS = {Class.class};
    private static final UnsafeOperations UNSAFE = UnsafeOperations.create();

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
            putMethodField(method, METHOD_ACCESSOR_FIELD, AndroidNewInstanceMethodAccessor.create());
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

    private static final class AndroidNewInstanceMethodAccessor implements InvocationHandler {
        static Object create() throws ClassNotFoundException {
            Class<?> methodAccessor = Class.forName("jdk.internal.reflect.MethodAccessor");
            return Proxy.newProxyInstance(
                methodAccessor.getClassLoader(),
                new Class<?>[] {methodAccessor},
                new AndroidNewInstanceMethodAccessor());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] arguments) {
            if ("toString".equals(method.getName()) && method.getParameterCount() == 0) {
                return getClass().getName();
            }
            if (!"invoke".equals(method.getName())) {
                throw new IllegalArgumentException("Unexpected MethodAccessor method: " + method);
            }
            Object target = arguments[0];
            Object[] newInstanceArguments = (Object[]) arguments[1];
            if (!(target instanceof ObjectStreamClass)) {
                throw new IllegalArgumentException("Unexpected ObjectStreamClass receiver: " + target);
            }
            if (newInstanceArguments == null
                || newInstanceArguments.length != 1
                || newInstanceArguments[0] != SerializableChild.class) {
                throw new IllegalArgumentException("Unexpected Android serialization target");
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

    private static Object getMethodField(Method method, String fieldName) {
        return UNSAFE.getReference(method, methodFieldOffset(fieldName));
    }

    private static void putMethodField(Method method, String fieldName, Object value) {
        UNSAFE.putReference(method, methodFieldOffset(fieldName), value);
    }

    private static long methodFieldOffset(String fieldName) {
        return UNSAFE.objectFieldOffset(Method.class, fieldName);
    }

    private static final class UnsafeOperations {
        private final Object unsafe;
        private final Method objectFieldOffset;
        private final Method getReference;
        private final Method putReference;

        private UnsafeOperations(Object unsafe, Method objectFieldOffset, Method getReference, Method putReference) {
            this.unsafe = unsafe;
            this.objectFieldOffset = objectFieldOffset;
            this.getReference = getReference;
            this.putReference = putReference;
        }

        static UnsafeOperations create() {
            try {
                Class<?> unsafeType = Class.forName("jdk.internal.misc.Unsafe");
                Field theUnsafe = unsafeType.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return new UnsafeOperations(
                    theUnsafe.get(null),
                    unsafeType.getMethod("objectFieldOffset", Class.class, String.class),
                    unsafeType.getMethod("getReference", Object.class, long.class),
                    unsafeType.getMethod("putReference", Object.class, long.class, Object.class));
            } catch (ReflectiveOperationException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        long objectFieldOffset(Class<?> type, String fieldName) {
            try {
                return (Long) objectFieldOffset.invoke(unsafe, type, fieldName);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }

        Object getReference(Object target, long offset) {
            try {
                return getReference.invoke(unsafe, target, offset);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }

        void putReference(Object target, long offset, Object value) {
            try {
                putReference.invoke(unsafe, target, offset, value);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
