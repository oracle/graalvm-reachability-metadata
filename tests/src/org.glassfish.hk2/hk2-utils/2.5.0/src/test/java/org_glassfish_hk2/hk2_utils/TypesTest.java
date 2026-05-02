/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.jvnet.tiger_types.Types;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesTest {
    @Test
    public void detectsOverriddenMethodDeclaredOnBaseClass() throws NoSuchMethodException {
        final Method method = ChildService.class.getMethod("describe", String.class);

        assertThat(Types.isOverriding(method, ParentService.class)).isTrue();
    }

    @Test
    public void convertsGenericArrayTypeArgumentWithClassComponentToArrayClass() {
        final Type genericStringArrayType = new SimpleGenericArrayType(String.class);
        final ParameterizedType listType = Types.createParameterizedType(List.class, genericStringArrayType);

        assertThat(Types.getTypeArgument(listType, 0)).isEqualTo(String[].class);
    }

    public static class ParentService {
        public String describe(String value) {
            return "parent:" + value;
        }
    }

    public static final class ChildService extends ParentService {
        @Override
        public String describe(String value) {
            return "child:" + value;
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
