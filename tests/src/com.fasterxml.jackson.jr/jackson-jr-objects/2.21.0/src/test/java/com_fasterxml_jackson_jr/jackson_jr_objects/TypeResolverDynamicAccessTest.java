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

import java.lang.reflect.Type;

import static org.assertj.core.api.Assertions.assertThat;

public class TypeResolverDynamicAccessTest {
    @Test
    void resolvesGenericArrayTypesFromBoundTypeVariables() throws Exception {
        TypeResolver resolver = new TypeResolver();
        ResolvedType declaringType = resolver.resolve(
                TypeBindings.emptyBindings(),
                StringArrayContainer.class.getGenericSuperclass());
        Type genericArrayType = GenericArrayContainer.class
                .getDeclaredMethod("setValues", Object[].class)
                .getGenericParameterTypes()[0];

        ResolvedType resolvedArrayType = resolver.resolve(declaringType.typeBindings(), genericArrayType);

        assertThat(resolvedArrayType.isArray()).isTrue();
        assertThat(resolvedArrayType.erasedType()).isEqualTo(String[].class);
        assertThat(resolvedArrayType.elementType().erasedType()).isEqualTo(String.class);
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
}
