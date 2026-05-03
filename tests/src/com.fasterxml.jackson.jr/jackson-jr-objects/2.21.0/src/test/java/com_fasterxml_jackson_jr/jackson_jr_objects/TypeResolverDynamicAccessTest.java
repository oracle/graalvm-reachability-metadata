/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_jr.jackson_jr_objects;

import com.fasterxml.jackson.jr.ob.impl.POJODefinition;
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
    void resolvesSyntheticGenericArrayTypesFromLibraryComponentTypes() {
        TypeResolver resolver = new TypeResolver();
        ResolvedType componentType = resolver.resolve(TypeBindings.emptyBindings(), POJODefinition.class);
        GenericArrayType genericArrayType = new SyntheticGenericArrayType(componentType);

        ResolvedType resolvedArrayType = resolver.resolve(TypeBindings.emptyBindings(), genericArrayType);

        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(POJODefinition[].class);
        assertThat(resolvedArrayType.erasedType().getComponentType()).isEqualTo(POJODefinition.class);
        assertThat(resolvedArrayType.elementType()).isEqualTo(componentType);
    }

    @Test
    void resolvesGenericArrayTypesFromBoundTypeVariables() throws Exception {
        TypeResolver resolver = new TypeResolver();
        ResolvedType declaringType = resolver.resolve(
                TypeBindings.emptyBindings(),
                StringArrayContainer.class.getGenericSuperclass());
        Type genericArrayType = GenericArrayContainer.class
                .getDeclaredField("values")
                .getGenericType();

        ResolvedType resolvedArrayType = resolver.resolve(declaringType.typeBindings(), genericArrayType);

        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(String[].class);
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(String.class);
    }

    @Test
    void resolvesGenericArrayTypesNestedInParameterizedTypes() throws Exception {
        TypeResolver resolver = new TypeResolver();
        Type listWithGenericArrayElement = GenericArrayListContainer.class
                .getDeclaredField("values")
                .getGenericType();

        ResolvedType resolvedListType = resolver.resolve(TypeBindings.emptyBindings(), listWithGenericArrayElement);
        ResolvedType resolvedArrayType = resolvedListType.typeParametersFor(List.class).get(0);

        assertThat(resolvedListType.erasedType()).isEqualTo(List.class);
        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(POJODefinition[].class);
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(POJODefinition.class);
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

    static final class GenericArrayListContainer<T extends POJODefinition> {
        private List<T[]> values;

        public List<T[]> getValues() {
            return values;
        }

        public void setValues(List<T[]> values) {
            this.values = values;
        }
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
