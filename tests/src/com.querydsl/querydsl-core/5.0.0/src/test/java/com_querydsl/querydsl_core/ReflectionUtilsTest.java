/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.querydsl_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.core.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Set;

import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void getFieldOrNullFindsDeclaredFieldOnSuperclass() {
        Field field = ReflectionUtils.getFieldOrNull(EmployeeRecord.class, "identifier");

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("identifier");
        assertThat(field.getType()).isEqualTo(Long.class);
        assertThat(field.getDeclaringClass()).isEqualTo(BaseRecord.class);
    }

    @Test
    void getGetterOrNullFindsDeclaredGetterOnClassAndSuperclass() {
        Method displayNameGetter = ReflectionUtils.getGetterOrNull(EmployeeRecord.class, "displayName", String.class);
        Method activeGetter = ReflectionUtils.getGetterOrNull(EmployeeRecord.class, "active", boolean.class);

        assertThat(displayNameGetter).isNotNull();
        assertThat(displayNameGetter.getName()).isEqualTo("getDisplayName");
        assertThat(displayNameGetter.getReturnType()).isEqualTo(String.class);
        assertThat(activeGetter).isNotNull();
        assertThat(activeGetter.getName()).isEqualTo("isActive");
        assertThat(activeGetter.getReturnType()).isEqualTo(boolean.class);
        assertThat(activeGetter.getDeclaringClass()).isEqualTo(BaseRecord.class);
    }

    @Test
    void getFieldsCollectsDeclaredFieldsAcrossHierarchy() {
        Set<Field> fields = ReflectionUtils.getFields(EmployeeRecord.class);

        assertThat(fields)
                .extracting(Field::getName)
                .contains("identifier", "active", "displayName");
    }

    @Test
    void getTypeParameterAsClassConvertsGenericArrayTypeToArrayClass() {
        Type type = new SingleParameterType(new ArrayParameterType(String.class));

        Class<?> result = ReflectionUtils.getTypeParameterAsClass(type, 0);

        assertThat(result).isEqualTo(String[].class);
    }

    public static class BaseRecord {

        private Long identifier;

        private boolean active;

        public boolean isActive() {
            return active;
        }
    }

    public static class EmployeeRecord extends BaseRecord {

        private String displayName;

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final class SingleParameterType implements ParameterizedType {

        private final Type parameter;

        private SingleParameterType(Type parameter) {
            this.parameter = parameter;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[] {parameter};
        }

        @Override
        public Type getRawType() {
            return TypeCarrier.class;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }

    private static final class ArrayParameterType implements GenericArrayType {

        private final Type componentType;

        private ArrayParameterType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }

    private static final class TypeCarrier<T> {
    }
}
