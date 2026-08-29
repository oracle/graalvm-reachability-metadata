/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.collection.internal.StandardListSemantics;
import org.hibernate.collection.spi.CollectionSemantics;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.IntegerJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.StringJavaType;
import org.hibernate.type.descriptor.java.spi.BasicCollectionJavaType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BasicCollectionJavaTypeTest {

    @Test
    public void unwrapsListsToObjectAndPrimitiveArrays() {
        BasicCollectionJavaType<List<String>, String> strings = listType(
                String.class,
                StringJavaType.INSTANCE
        );
        BasicCollectionJavaType<List<Integer>, Integer> integers = listType(
                Integer.class,
                new PrimitiveIntegerJavaType()
        );

        String[] objectArray = strings.unwrap(
                List.of("hibernate", "native"),
                String[].class,
                null
        );
        int[] primitiveArray = integers.unwrap(List.of(6, 1), int[].class, null);

        assertThat(objectArray).containsExactly("hibernate", "native");
        assertThat(primitiveArray).containsExactly(6, 1);
    }

    private static <E> BasicCollectionJavaType<List<E>, E> listType(
            Class<E> elementClass,
            JavaType<E> elementJavaType) {
        ParameterizedType listType = new ListParameterizedType(elementClass);
        @SuppressWarnings("unchecked")
        CollectionSemantics<List<E>, E> semantics =
                (CollectionSemantics<List<E>, E>) (CollectionSemantics<?, ?>)
                        StandardListSemantics.INSTANCE;
        return new BasicCollectionJavaType<>(listType, elementJavaType, semantics);
    }

    private static class ListParameterizedType implements ParameterizedType {
        private final Type elementType;

        private ListParameterizedType(Type elementType) {
            this.elementType = elementType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{elementType};
        }

        @Override
        public Type getRawType() {
            return List.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    public static class PrimitiveIntegerJavaType extends IntegerJavaType {
        @Override
        public <X> X unwrap(Integer value, Class<X> type, WrapperOptions options) {
            if (type == int.class) {
                @SuppressWarnings("unchecked")
                X primitiveValue = (X) value;
                return primitiveValue;
            }
            return super.unwrap(value, type, options);
        }
    }
}
