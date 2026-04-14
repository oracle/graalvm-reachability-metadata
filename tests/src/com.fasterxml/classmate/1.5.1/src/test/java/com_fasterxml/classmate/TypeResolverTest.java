/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml.classmate;

import java.util.List;

import com.fasterxml.classmate.GenericType;
import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.classmate.TypeResolver;
import com.fasterxml.classmate.types.ResolvedArrayType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeResolverTest {

    private static final TypeResolver TYPE_RESOLVER = new TypeResolver();

    @Test
    void createsArrayTypeFromElementType() {
        ResolvedArrayType arrayType = TYPE_RESOLVER.arrayType(String.class);

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.getErasedType()).isEqualTo(String[].class);
        assertThat(arrayType.getArrayElementType().getErasedType()).isEqualTo(String.class);
        assertThat(arrayType.getBriefDescription()).isEqualTo("java.lang.String[]");
    }

    @Test
    void resolvesGenericArrayTypeFromGenericTypeToken() {
        ResolvedType arrayType = TYPE_RESOLVER.resolve(new GenericType<List<String>[]>() { });
        ResolvedType elementType = arrayType.getArrayElementType();

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.getErasedType()).isEqualTo(List[].class);
        assertThat(elementType.getErasedType()).isEqualTo(List.class);
        assertThat(elementType.getTypeParameters())
                .singleElement()
                .satisfies(typeParameter -> assertThat(typeParameter.getErasedType()).isEqualTo(String.class));
    }
}
