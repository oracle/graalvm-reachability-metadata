/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_yaml.snakeyaml;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.introspector.GenericProperty;

public class GenericPropertyTest {

    @Test
    void resolvesGenericArrayTypeArgumentsToArrayClasses() {
        Type genericArrayArgument = new ClassComponentGenericArrayType(String.class);
        Type propertyType = new SingleArgumentParameterizedType(List.class, genericArrayArgument);
        GenericProperty property = new TestGenericProperty("items", List.class, propertyType);

        Class<?>[] actualTypeArguments = property.getActualTypeArguments();

        assertThat(actualTypeArguments).containsExactly(String[].class);
        assertThat(property.getActualTypeArguments()).isSameAs(actualTypeArguments);
    }

    private static final class TestGenericProperty extends GenericProperty {
        private TestGenericProperty(String name, Class<?> propertyClass, Type type) {
            super(name, propertyClass, type);
        }

        @Override
        public void set(Object object, Object value) {
            throw new UnsupportedOperationException("set is not used in this test");
        }

        @Override
        public Object get(Object object) {
            throw new UnsupportedOperationException("get is not used in this test");
        }

        @Override
        public List<Annotation> getAnnotations() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
            return null;
        }
    }

    private static final class SingleArgumentParameterizedType implements ParameterizedType {
        private final Type rawType;
        private final Type actualTypeArgument;

        private SingleArgumentParameterizedType(Type rawType, Type actualTypeArgument) {
            this.rawType = rawType;
            this.actualTypeArgument = actualTypeArgument;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {actualTypeArgument};
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

    private static final class ClassComponentGenericArrayType implements GenericArrayType {
        private final Class<?> componentType;

        private ClassComponentGenericArrayType(Class<?> componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}
