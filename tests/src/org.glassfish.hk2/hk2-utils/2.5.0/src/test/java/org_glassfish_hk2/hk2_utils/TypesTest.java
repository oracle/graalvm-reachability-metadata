/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_hk2.hk2_utils;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.glassfish.hk2.utilities.reflection.GenericArrayTypeImpl;
import org.glassfish.hk2.utilities.reflection.ParameterizedTypeImpl;
import org.glassfish.hk2.utilities.reflection.ReflectionHelper;
import org.glassfish.hk2.utilities.reflection.internal.MethodWrapperImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TypesTest {
    @Test
    public void treatsOverriddenMethodsAsEquivalentWrappers() throws NoSuchMethodException {
        final Method childMethod = ChildService.class.getMethod("describe", String.class);
        final Method parentMethod = ParentService.class.getMethod("describe", String.class);

        assertThat(new MethodWrapperImpl(childMethod)).isEqualTo(new MethodWrapperImpl(parentMethod));
    }

    @Test
    public void convertsGenericArrayTypeArgumentWithClassComponentToArrayClass() {
        final Type genericStringArrayType = new GenericArrayTypeImpl(String.class);
        final ParameterizedType listType = new ParameterizedTypeImpl(List.class, genericStringArrayType);

        assertThat(listType.getRawType()).isEqualTo(List.class);
        assertThat(ReflectionHelper.getRawClass(listType.getActualTypeArguments()[0])).isEqualTo(String[].class);
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
}
