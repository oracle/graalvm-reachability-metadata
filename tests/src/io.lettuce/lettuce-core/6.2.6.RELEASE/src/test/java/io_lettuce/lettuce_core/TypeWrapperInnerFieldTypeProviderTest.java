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
    void deserializesFieldBackedGenericType() throws Exception {
        Field field = GenericFieldFixture.class.getField("names");
        Type fieldType = typeWrapperForField(field);

        Type restored = serializeAndDeserialize(fieldType);

        assertThat(restored).isInstanceOfSatisfying(ParameterizedType.class, parameterizedType -> {
            assertThat(parameterizedType.getRawType()).isEqualTo(List.class);
            assertThat(parameterizedType.getActualTypeArguments()).containsExactly(String.class);
        });
    }

    private static Type typeWrapperForField(Field field) throws Exception {
        Class<?> typeWrapperClass = Class.forName("io.lettuce.core.dynamic.support.TypeWrapper");
        Method forField = typeWrapperClass.getDeclaredMethod("forField", Field.class);
        forField.setAccessible(true);
        return (Type) forField.invoke(null, field);
    }

    private static Type serializeAndDeserialize(Type type) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(type);
        }

        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            return (Type) inputStream.readObject();
        }
    }

    public static final class GenericFieldFixture {

        public List<String> names;
    }
}
