/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import com.querydsl.core.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void getFieldOrNullFindsDeclaredAndInheritedFields() {
        Field declaredField = ReflectionUtils.getFieldOrNull(ReflectionFixture.class, "declaredName");
        Field inheritedField = ReflectionUtils.getFieldOrNull(ReflectionFixture.class, "baseName");

        assertThat(declaredField)
                .isNotNull()
                .extracting(Field::getName)
                .isEqualTo("declaredName");
        assertThat(inheritedField)
                .isNotNull()
                .extracting(Field::getName)
                .isEqualTo("baseName");
    }

    @Test
    void getGetterOrNullFindsDeclaredAndInheritedAccessors() {
        Method declaredGetter = ReflectionUtils.getGetterOrNull(ReflectionFixture.class, "declaredName", String.class);
        Method inheritedGetter = ReflectionUtils.getGetterOrNull(ReflectionFixture.class, "active", boolean.class);

        assertThat(declaredGetter)
                .isNotNull()
                .extracting(Method::getName)
                .isEqualTo("getDeclaredName");
        assertThat(inheritedGetter)
                .isNotNull()
                .extracting(Method::getName)
                .isEqualTo("isActive");
    }

    @Test
    void getFieldsIncludesDeclaredFieldsFromClassHierarchy() {
        assertThat(ReflectionUtils.getFields(ReflectionFixture.class))
                .extracting(Field::getName)
                .contains("declaredName", "baseName");
    }

    @Test
    void getTypeParameterAsClassResolvesGenericArrayParameters() {
        Type stringArrayParameter = new GenericArrayTypeFixture(String.class);
        Type parameterizedType = new ParameterizedTypeFixture(GenericContainer.class, stringArrayParameter);

        Class<?> resolvedType = ReflectionUtils.getTypeParameterAsClass(parameterizedType, 0);

        assertThat(resolvedType).isEqualTo(String[].class);
    }

    private static class BaseReflectionFixture {
        private String baseName = "base";

        boolean isActive() {
            return true;
        }
    }

    private static final class ReflectionFixture extends BaseReflectionFixture {
        private String declaredName = "declared";

        String getDeclaredName() {
            return declaredName;
        }
    }

    private static final class GenericContainer<T> {
    }

    private static final class GenericArrayTypeFixture implements GenericArrayType {
        private final Type componentType;

        private GenericArrayTypeFixture(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }

    private static final class ParameterizedTypeFixture implements ParameterizedType {
        private final Type rawType;
        private final Type[] arguments;

        private ParameterizedTypeFixture(Type rawType, Type... arguments) {
            this.rawType = rawType;
            this.arguments = arguments.clone();
        }

        @Override
        public Type[] getActualTypeArguments() {
            return arguments.clone();
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
