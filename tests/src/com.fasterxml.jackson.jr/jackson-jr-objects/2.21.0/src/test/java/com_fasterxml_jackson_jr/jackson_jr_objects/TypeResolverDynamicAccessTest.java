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

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverDynamicAccessTest {
    @Test
    void resolvesSyntheticGenericArrayTypesIntoArrayClasses() {
        TypeResolver resolver = new TypeResolver();
        Class<?> elementType = runtimeSelectedElementType();

        ResolvedType resolvedArrayType = resolver.resolve(
                TypeBindings.emptyBindings(),
                new SyntheticGenericArrayType(elementType));

        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(arrayTypeFor(elementType));
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(elementType);
    }

    @Test
    void resolvesGenericArrayTypesFromBoundTypeVariables() throws Exception {
        TypeResolver resolver = new TypeResolver();
        Class<?> elementType = runtimeSelectedElementType();
        Class<? extends GenericArrayContainer<?>> containerType = runtimeSelectedContainerType();
        ResolvedType declaringType = resolver.resolve(
                TypeBindings.emptyBindings(),
                containerType.getGenericSuperclass());
        Type genericArrayType = GenericArrayContainer.class
                .getDeclaredMethod("setValues", Object[].class)
                .getGenericParameterTypes()[0];

        ResolvedType resolvedArrayType = resolver.resolve(declaringType.typeBindings(), genericArrayType);

        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(arrayTypeFor(elementType));
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(elementType);
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

    static final class StringArrayContainer extends GenericArrayContainer<String> {
    }

    static final class CharSequenceArrayContainer extends GenericArrayContainer<CharSequence> {
    }

    private static Class<?> runtimeSelectedElementType() {
        return Thread.currentThread().getName().isEmpty() ? CharSequence.class : String.class;
    }

    private static Class<? extends GenericArrayContainer<?>> runtimeSelectedContainerType() {
        if (Thread.currentThread().getName().isEmpty()) {
            return CharSequenceArrayContainer.class;
        }
        return StringArrayContainer.class;
    }

    private static Class<?> arrayTypeFor(Class<?> elementType) {
        if (elementType == String.class) {
            return String[].class;
        }
        return CharSequence[].class;
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
