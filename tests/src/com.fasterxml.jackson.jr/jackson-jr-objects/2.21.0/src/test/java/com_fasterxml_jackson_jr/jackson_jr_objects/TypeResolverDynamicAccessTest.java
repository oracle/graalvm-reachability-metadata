/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.type.ResolvedType;
import com.fasterxml.jackson.jr.type.TypeBindings;
import com.fasterxml.jackson.jr.type.TypeResolver;
import org.junit.jupiter.api.Test;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverDynamicAccessTest {
    @Test
    void resolvesSyntheticGenericArrayTypesFromRuntimeResolvedComponentTypes() {
        TypeResolver resolver = new TypeResolver();
        Object runtimeComponentValue = runtimeComponentValue();
        Class<?> componentClass = runtimeComponentValue.getClass();
        ResolvedType componentType = resolver.resolve(TypeBindings.emptyBindings(), componentClass);
        GenericArrayType genericArrayType = new SyntheticGenericArrayType(componentClass);

        ResolvedType resolvedArrayType = resolver.resolve(TypeBindings.emptyBindings(), genericArrayType);

        assertThat(componentClass).isEqualTo(ArrayComponentValue.class);
        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType().getComponentType()).isEqualTo(componentClass);
        assertThat(resolvedArrayType.elementType()).isEqualTo(componentType);
    }

    @Test
    void resolvesGenericArrayTypesFromBoundTypeVariables() throws Exception {
        TypeResolver resolver = new TypeResolver();
        ResolvedType declaringType = resolver.resolve(
                TypeBindings.emptyBindings(),
                ArrayComponentValueContainer.class.getGenericSuperclass());
        Type genericArrayType = GenericArrayContainer.class
                .getDeclaredField("values")
                .getGenericType();

        ResolvedType resolvedArrayType = resolver.resolve(declaringType.typeBindings(), genericArrayType);

        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(ArrayComponentValue[].class);
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(ArrayComponentValue.class);
    }

    @Test
    void resolvesGenericArrayTypesWithParameterizedComponents() throws Exception {
        TypeResolver resolver = new TypeResolver();
        Type genericArrayType = ParameterizedComponentArrayContainer.class
                .getDeclaredField("values")
                .getGenericType();

        ResolvedType resolvedArrayType = resolver.resolve(TypeBindings.emptyBindings(), genericArrayType);

        assertThat(genericArrayType).isInstanceOf(GenericArrayType.class);
        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(List[].class);
        assertThat(resolvedArrayType.erasedType().getComponentType()).isEqualTo(List.class);
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(List.class);
        assertThat(resolvedArrayType.elementType().typeParametersFor(List.class).get(0).erasedType())
                .isEqualTo(Number.class);
    }

    static class GenericArrayContainer<T> {
        private T[] values;

        public T[] getValues() {
            return values;
        }

        public void setValues(T[] values) {
            this.values = values;
        }
    }

    static final class ArrayComponentValueContainer extends GenericArrayContainer<ArrayComponentValue> {
    }

    static final class ParameterizedComponentArrayContainer {
        private List<? extends Number>[] values;

        public List<? extends Number>[] getValues() {
            return values;
        }

        public void setValues(List<? extends Number>[] values) {
            this.values = values;
        }
    }

    private static Object runtimeComponentValue() {
        return new ArrayComponentValue();
    }

    static final class ArrayComponentValue {
    }

    static final class SyntheticGenericArrayType implements GenericArrayType {
        private final Type componentType;

        SyntheticGenericArrayType(Type componentType) {
            this.componentType = componentType;
        }

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}
