/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TypeWrapperInnerFieldTypeProviderTest {

    @Test
    void deserializesFieldTypeProviderBackedByField() throws Exception {
        Field field = FieldFixtures.class.getField("values");
        Type wrappedType = wrapFieldType(field);

        Type restoredType = serializeAndDeserialize(wrappedType);

        assertThat(restoredType).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) restoredType;
        assertThat(parameterizedType.getRawType()).isEqualTo(List.class);
        assertThat(parameterizedType.getActualTypeArguments()).containsExactly(String.class);
    }

    private static Type wrapFieldType(Field field) throws Exception {
        Class<?> typeWrapper = Class.forName("io.lettuce.core.dynamic.support.TypeWrapper");
        Method forField = typeWrapper.getDeclaredMethod("forField", Field.class);
        forField.setAccessible(true);
        return (Type) forField.invoke(null, field);
    }

    private static Type serializeAndDeserialize(Type type) throws IOException, ClassNotFoundException {
        byte[] serialized;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(type);
            serialized = bytes.toByteArray();
        }

        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (Type) input.readObject();
        }
    }

    public static final class FieldFixtures {

        public List<String> values;
    }
}
