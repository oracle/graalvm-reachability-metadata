/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.apache.aries.blueprint.container.GenericType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericTypeTest {
    @Test
    void parsesNamedParameterizedAndArrayTypes() throws Exception {
        ClassLoader loader = GenericTypeTest.class.getClassLoader();
        GenericType map = GenericType.parse("java.util.Map<java.lang.String,java.lang.Integer>", loader);
        assertThat(map.getRawClass()).isEqualTo(Map.class);
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.getActualTypeArgument(0).getRawClass()).isEqualTo(String.class);
        assertThat(map.getActualTypeArgument(1).getRawClass()).isEqualTo(Integer.class);
        GenericType array = GenericType.parse("java.lang.String[]", loader);
        assertThat(array.getRawClass()).isEqualTo(String[].class);
        assertThat(array.getActualTypeArgument(0).getRawClass()).isEqualTo(String.class);
        assertThat(array.toString()).isEqualTo("java.lang.String[]");
        assertThat(GenericType.parse("int[]", loader).getRawClass()).isEqualTo(int[].class);
    }

    @Test
    void representsAnArrayOfParameterizedLists() {
        GenericArrayType array = () -> new StringListType();
        GenericType type = new GenericType(array);
        assertThat(type.getRawClass()).isEqualTo(List[].class);
        assertThat(type.getActualTypeArgument(0).getRawClass()).isEqualTo(List.class);
        assertThat(type.getActualTypeArgument(0).getActualTypeArgument(0).getRawClass()).isEqualTo(String.class);
        assertThat(type.toString()).isEqualTo("java.util.List<java.lang.String>[]");
    }

    private static final class StringListType implements ParameterizedType {
        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{String.class};
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
}
