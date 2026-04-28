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
import java.util.Arrays;

import org.glassfish.jaxb.core.v2.model.nav.Navigator;
import org.junit.jupiter.api.Test;

public class ReflectionNavigatorTest {
    @Test
    public void inspectsDefaultConstructors() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();

        assertThat(navigator.hasDefaultConstructor(DefaultConstructible.class)).isTrue();
        assertThat(navigator.hasDefaultConstructor(ConstructorWithArgumentsOnly.class)).isFalse();
    }

    @Test
    public void resolvesPublicEnumConstantFields() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();

        Field[] constants = navigator.getEnumConstants(SampleEnum.class);

        assertThat(Arrays.stream(constants).map(Field::getName))
                .containsExactly("FIRST", "SECOND");
    }

    @Test
    public void loadsObjectFactoryFromReferencePackage() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();

        Class<?> factory = navigator.loadObjectFactory(
                ReflectionNavigatorTest.class,
                ReflectionNavigatorTest.class.getPackageName());

        assertThat(factory).isSameAs(ObjectFactory.class);
    }

    @Test
    public void resolvesPrimitiveArrayFieldType() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();
        Field primitiveArray = navigator.getDeclaredField(FieldHolder.class, "primitiveArray");

        Type fieldType = navigator.getFieldType(primitiveArray);

        assertThat(fieldType).isSameAs(int[].class);
    }

    @Test
    public void normalizesGenericArrayTypeArgumentsBackedByClasses() throws Exception {
        Navigator<Type, Class<?>, Field, Method> navigator = navigator();
        Type genericArray = new ClassBackedGenericArrayType(String.class);
        Type parameterizedType = new SingleArgumentParameterizedType(GenericBox.class, genericArray);

        Type argument = navigator.getTypeArgument(parameterizedType, 0);

        assertThat(argument).isSameAs(String[].class);
    }

    @SuppressWarnings("unchecked")
    private static Navigator<Type, Class<?>, Field, Method> navigator() throws Exception {
        Class<?> navigatorType = Class.forName("org.glassfish.jaxb.core.v2.model.nav.ReflectionNavigator");
        Method getInstance = navigatorType.getDeclaredMethod("getInstance");
        getInstance.setAccessible(true);
        return (Navigator<Type, Class<?>, Field, Method>) getInstance.invoke(null);
    }

    private static final class DefaultConstructible {
        private DefaultConstructible() {
        }
    }

    private static final class ConstructorWithArgumentsOnly {
        private ConstructorWithArgumentsOnly(String value) {
            assertThat(value).isNotNull();
        }
    }

    private enum SampleEnum {
        FIRST,
        SECOND
    }

    private static final class FieldHolder {
        private int[] primitiveArray;
    }

    private static final class GenericBox<T> {
    }

    private static final class SingleArgumentParameterizedType implements ParameterizedType {
        private final Class<?> rawType;
        private final Type argument;

        private SingleArgumentParameterizedType(Class<?> rawType, Type argument) {
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

    private static final class ClassBackedGenericArrayType implements GenericArrayType {
        private final Class<?> componentType;

        private ClassBackedGenericArrayType(Class<?> componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}

final class ObjectFactory {
    private ObjectFactory() {
    }
}
