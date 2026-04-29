/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.serializers.FieldSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

public class FieldSerializerGenericsUtilTest {
    @Test
    void serializesInheritedFieldsWithTypeVariableArrayGenerics() {
        Kryo kryo = new Kryo();
        FieldSerializer<StringArrayHolder> serializer = new FieldSerializer<>(kryo, StringArrayHolder.class);
        kryo.register(StringArrayHolder.class, serializer);

        StringArrayHolder original = new StringArrayHolder();
        original.arrays.add(new String[] {"alpha", "beta"});
        original.arrays.add(new String[] {"gamma"});

        Output output = new Output(128, -1);
        kryo.writeObject(output, original);

        StringArrayHolder restored = kryo.readObject(new Input(output.toBytes()), StringArrayHolder.class);

        assertThat(serializer.getFields()).hasSize(1);
        assertThat(restored.arrays).hasSize(2);
        assertThat(restored.arrays.get(0)).containsExactly("alpha", "beta");
        assertThat(restored.arrays.get(1)).containsExactly("gamma");
    }

    @Test
    void resolvesClassComponentGenericArrayTypes() throws Exception {
        Kryo kryo = new Kryo();
        FieldSerializer<ClassComponentArrayHolder> serializer = new FieldSerializer<>(kryo, ClassComponentArrayHolder.class);
        Field arraysField = ClassComponentArrayHolder.class.getDeclaredField("arrays");
        Type arrayListOfStringArrays = new SimpleParameterizedType(
            ArrayList.class,
            new SimpleGenericArrayType(String.class)
        );

        Class<?>[] staticGenerics = getGenerics(arrayListOfStringArrays, kryo);
        Class<?>[] fieldGenerics = computeFieldGenerics(serializer, arrayListOfStringArrays, arraysField, ArrayList.class);

        assertThat(staticGenerics).containsExactly(String[].class);
        assertThat(fieldGenerics).containsExactly(String[].class);
    }

    public static class GenericArrayHolder<T> {
        public ArrayList<T[]> arrays = new ArrayList<>();
    }

    public static class StringArrayHolder extends GenericArrayHolder<String> {
    }

    public static class ClassComponentArrayHolder {
        public ArrayList<String[]> arrays = new ArrayList<>();
    }

    private static Class<?>[] getGenerics(Type genericType, Kryo kryo) throws Exception {
        Class<?> utilityClass = genericsUtilClass();
        Method getGenerics = utilityClass.getDeclaredMethod("getGenerics", Type.class, Kryo.class);
        getGenerics.setAccessible(true);
        return (Class<?>[]) getGenerics.invoke(null, genericType, kryo);
    }

    private static Class<?>[] computeFieldGenerics(
        FieldSerializer<?> serializer,
        Type fieldGenericType,
        Field field,
        Class<?> fieldClass
    ) throws Exception {
        Class<?> utilityClass = genericsUtilClass();
        Constructor<?> constructor = utilityClass.getDeclaredConstructor(FieldSerializer.class);
        constructor.setAccessible(true);
        Object utility = constructor.newInstance(serializer);
        Method computeFieldGenerics = utilityClass.getDeclaredMethod(
            "computeFieldGenerics",
            Type.class,
            Field.class,
            Class[].class
        );
        computeFieldGenerics.setAccessible(true);
        return (Class<?>[]) computeFieldGenerics.invoke(utility, fieldGenericType, field, new Class[] {fieldClass});
    }

    private static Class<?> genericsUtilClass() throws ClassNotFoundException {
        return Class.forName("com.esotericsoftware.kryo.serializers.FieldSerializerGenericsUtil");
    }

    private static final class SimpleParameterizedType implements ParameterizedType {
        private final Class<?> rawType;
        private final Type[] actualTypeArguments;

        private SimpleParameterizedType(Class<?> rawType, Type... actualTypeArguments) {
            this.rawType = rawType;
            this.actualTypeArguments = Arrays.copyOf(actualTypeArguments, actualTypeArguments.length);
        }

        @Override
        public Type[] getActualTypeArguments() {
            return Arrays.copyOf(actualTypeArguments, actualTypeArguments.length);
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

    private static final class SimpleGenericArrayType implements GenericArrayType {
        private final Type genericComponentType;

        private SimpleGenericArrayType(Type genericComponentType) {
            this.genericComponentType = genericComponentType;
        }

        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }
    }
}
