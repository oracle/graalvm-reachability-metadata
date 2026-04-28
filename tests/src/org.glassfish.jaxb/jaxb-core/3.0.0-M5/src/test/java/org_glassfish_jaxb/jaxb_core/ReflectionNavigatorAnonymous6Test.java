/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.glassfish.jaxb.core.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

public class ReflectionNavigatorAnonymous6Test {
    @Test
    public void erasesGenericArrayTypeToRuntimeArrayClass() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();
        Type genericArrayType = new ParameterizedComponentArrayType(List.class, String.class);

        Class<?> erasedType = navigator.asDecl(genericArrayType);

        assertThat(erasedType).isSameAs(List[].class);
        assertThat(erasedType.getComponentType()).isSameAs(List.class);
    }

    @SuppressWarnings("unchecked")
    private static Navigator<Type, Class<?>, Field, Method> navigator() throws Exception {
        Class<?> navigatorType = Class.forName("org.glassfish.jaxb.core.v2.model.nav.ReflectionNavigator");
        Method getInstance = navigatorType.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        return (Navigator<Type, Class<?>, Field, Method>) getInstance.invoke(null);
    }

    private static final class ParameterizedComponentArrayType implements GenericArrayType {
        private final ParameterizedType componentType;

        private ParameterizedComponentArrayType(Class<?> rawType, Type argument) {
            componentType = new SimpleParameterizedType(rawType, argument);
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }

    private static final class SimpleParameterizedType implements ParameterizedType {
        private final Class<?> rawType;
        private final Type argument;

        private SimpleParameterizedType(Class<?> rawType, Type argument) {
            this.rawType = rawType;
            this.argument = argument;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {argument};
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
}
