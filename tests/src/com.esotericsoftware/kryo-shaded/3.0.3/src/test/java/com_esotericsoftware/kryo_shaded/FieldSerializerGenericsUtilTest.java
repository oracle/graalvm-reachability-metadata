/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FieldSerializerGenericsUtilTest {
    @Test
    void serializesParameterizedFieldsWhoseArgumentsAreGenericArrays() {
        Kryo kryo = newKryo();
        FieldSerializer<GenericArrayContainer> serializer =
                new FieldSerializer<>(kryo, GenericArrayContainer.class, new Class[] {String.class});
        GenericArrayContainer<String> original = new GenericArrayContainer<>();
        original.values = new ArrayList<>();
        original.values.add(new String[] {"alpha", "beta"});
        original.values.add(new String[] {"gamma"});

        GenericArrayContainer read = roundTrip(kryo, serializer, original, GenericArrayContainer.class);

        assertThat(read.values).hasSize(2);
        assertThat(read.values.get(0)).isInstanceOf(String[].class);
        assertThat((String[]) read.values.get(0)).containsExactly("alpha", "beta");
        assertThat((String[]) read.values.get(1)).containsExactly("gamma");
    }

    @Test
    void copiesParameterizedFieldsWhoseArgumentsAreGenericArrays() {
        Kryo kryo = newKryo();
        FieldSerializer<GenericArrayContainer> serializer =
                new FieldSerializer<>(kryo, GenericArrayContainer.class, new Class[] {Integer.class});
        GenericArrayContainer<Integer> original = new GenericArrayContainer<>();
        original.values = new ArrayList<>();
        original.values.add(new Integer[] {1, 2, 3});

        GenericArrayContainer copy = serializer.copy(kryo, original);

        assertThat(copy).isNotSameAs(original);
        assertThat(copy.values).isNotSameAs(original.values);
        assertThat(copy.values).hasSize(1);
        assertThat(copy.values.get(0)).isInstanceOf(Integer[].class);
        assertThat((Integer[]) copy.values.get(0)).containsExactly(1, 2, 3);
    }

    @Test
    void resolvesSyntheticGenericArrayTypeArgumentsWithConcreteComponents() throws ReflectiveOperationException {
        Kryo kryo = newKryo();
        FieldSerializer<GenericArrayContainer> serializer =
                new FieldSerializer<>(kryo, GenericArrayContainer.class, new Class[] {String.class});
        Type genericType = new SingleArgumentParameterizedType(
                List.class,
                new ClassComponentGenericArrayType(String.class));

        Class[] directGenerics = invokeGetGenerics(genericType, kryo);
        Class[] computedGenerics = invokeComputeFieldGenerics(
                serializer,
                genericType,
                GenericArrayContainer.class.getField("values"),
                new Class[] {List.class});

        assertThat(directGenerics).containsExactly(String[].class);
        assertThat(computedGenerics).containsExactly(String[].class);
    }

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setReferences(false);
        return kryo;
    }

    private static <T> T roundTrip(Kryo kryo, FieldSerializer<T> serializer, T original, Class<T> type) {
        Output output = new Output(256, -1);
        serializer.write(kryo, output, original);
        kryo.reset();

        Input input = new Input(output.toBytes());
        T read = serializer.read(kryo, input, type);
        kryo.reset();
        return read;
    }

    private static Class[] invokeGetGenerics(Type genericType, Kryo kryo) throws ReflectiveOperationException {
        Class<?> utilClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializerGenericsUtil");
        Method getGenerics = utilClass.getDeclaredMethod("getGenerics", Type.class, Kryo.class);
        getGenerics.setAccessible(true);
        return (Class[]) getGenerics.invoke(null, genericType, kryo);
    }

    private static Class[] invokeComputeFieldGenerics(
            FieldSerializer<?> serializer, Type genericType, Field field, Class[] fieldClass)
            throws ReflectiveOperationException {
        Class<?> utilClass = Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializerGenericsUtil");
        Constructor<?> constructor = utilClass.getDeclaredConstructor(FieldSerializer.class);
        constructor.setAccessible(true);
        Object genericsUtil = constructor.newInstance(serializer);
        Method computeFieldGenerics = utilClass.getDeclaredMethod(
                "computeFieldGenerics", Type.class, Field.class, Class[].class);
        computeFieldGenerics.setAccessible(true);
        return (Class[]) computeFieldGenerics.invoke(genericsUtil, genericType, field, fieldClass);
    }

    public static class GenericArrayContainer<T> {
        public List<T[]> values;

        public GenericArrayContainer() {
        }
    }

    private static final class SingleArgumentParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type actualTypeArgument;

        private SingleArgumentParameterizedType(Type rawType, Type actualTypeArgument) {
            this.rawType = rawType;
            this.actualTypeArgument = actualTypeArgument;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {actualTypeArgument};
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    private static final class ClassComponentGenericArrayType implements GenericArrayType {
        private final Type componentType;

        private ClassComponentGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}
