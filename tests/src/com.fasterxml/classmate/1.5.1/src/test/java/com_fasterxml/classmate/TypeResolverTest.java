/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml.classmate;

import com.fasterxml.classmate.GenericType;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.types.ResolvedArrayType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TypeResolverTest {
    @Test
    void createsArrayTypesFromConcreteElementClasses() {
        TypeResolver resolver = new TypeResolver();

        ResolvedArrayType arrayType = resolver.arrayType(String.class);

        assertThat(arrayType.getErasedType()).isEqualTo(String[].class);
        assertThat(arrayType.getArrayElementType().getErasedType()).isEqualTo(String.class);
        assertThat(arrayType.getBriefDescription()).isEqualTo("java.lang.String[]");
    }

    @Test
    void resolvesGenericArrayTypesFromGenericTypeTokens() {
        TypeResolver resolver = new TypeResolver();

        ResolvedType resolvedType = resolver.resolve(new GenericType<List<String>[]>() { });

        assertThat(resolvedType.isArray()).isTrue();
        assertThat(resolvedType.getErasedType()).isEqualTo(List[].class);

        ResolvedType elementType = resolvedType.getArrayElementType();
        assertThat(elementType.getErasedType()).isEqualTo(List.class);
        assertThat(elementType.getTypeParameters()).hasSize(1);
        assertThat(elementType.getTypeParameters().get(0).getErasedType()).isEqualTo(String.class);
    }
}
