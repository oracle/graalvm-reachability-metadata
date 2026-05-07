/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import org.glassfish.jaxb.core.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionNavigatorTest {
    private final Navigator<Type, Class<?>, Field, Method> navigator = reflectionNavigator();

    @Test
    void discoversDefaultConstructorsAndEnumConstantFields() {
        assertThat(navigator.hasDefaultConstructor(DefaultConstructible.class)).isTrue();
        assertThat(navigator.hasDefaultConstructor(ConstructorWithArgumentOnly.class)).isFalse();

        Field[] fields = navigator.getEnumConstants(Color.class);

        assertThat(fields)
                .extracting(Field::getName)
                .containsExactly("RED", "GREEN");
    }

    @Test
    void loadsPackageObjectFactoryWithReferencePointClassLoader() {
        assertThat(ObjectFactory.class.getName()).endsWith(".ObjectFactory");

        Class<?> objectFactory = navigator.loadObjectFactory(
                ReflectionNavigatorTest.class,
                getClass().getPackageName());

        assertThat(objectFactory).isSameAs(ObjectFactory.class);
    }

    @Test
    void normalizesPrimitiveArrayFieldsAndClassBackedGenericArrayTypeArguments() {
        Field numbers = navigator.getDeclaredField(PrimitiveArrayHolder.class, "numbers");

        assertThat(navigator.getFieldType(numbers)).isSameAs(int[].class);

        Type stringArrayType = navigator.getTypeArgument(
                new SingleArgumentParameterizedType(List.class, new ClassBackedGenericArrayType(String.class)),
                0);

        assertThat(stringArrayType).isSameAs(String[].class);
    }

    @SuppressWarnings("unchecked")
    private static Navigator<Type, Class<?>, Field, Method> reflectionNavigator() {
        try {
            Class<?> navigatorClass = Class.forName("org.glassfish.jaxb.core.v2.model.nav.ReflectionNavigator");
            Method getInstance = navigatorClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            return (Navigator<Type, Class<?>, Field, Method>) getInstance.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ReflectionNavigator singleton should be available", e);
        }
    }

    private static class DefaultConstructible {
        private DefaultConstructible() {
        }
    }

    private static class ConstructorWithArgumentOnly {
        ConstructorWithArgumentOnly(String value) {
        }
    }

    public enum Color {
        RED,
        GREEN
    }

    private static class PrimitiveArrayHolder {
        int[] numbers;
    }

    private static class SingleArgumentParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type argument;

        SingleArgumentParameterizedType(Type rawType, Type argument) {
            this.rawType = rawType;
            this.argument = argument;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] { argument };
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

    private static class ClassBackedGenericArrayType implements GenericArrayType {
        private final Class<?> componentType;

        ClassBackedGenericArrayType(Class<?> componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}

class ObjectFactory {
}
