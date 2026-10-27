/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_guava.guava;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.reflect.TypeResolver;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import org.junit.jupiter.api.Test;

public class TypesInnerTypeVariableInvocationHandlerTest {
    @Test
    void resolvedDependentTypeVariableDelegatesTypeVariableMethods() {
        TypeVariable<?> outerTypeParameter =
                OuterTypeWithDependentInnerBound.class.getTypeParameters()[0];
        TypeVariable<?> dependentTypeParameter =
                OuterTypeWithDependentInnerBound.InnerType.class.getTypeParameters()[0];

        Type resolvedType =
                new TypeResolver()
                        .where(outerTypeParameter, String.class)
                        .resolveType(dependentTypeParameter);

        assertThat(resolvedType).isInstanceOf(TypeVariable.class);
        TypeVariable<?> resolvedTypeVariable = (TypeVariable<?>) resolvedType;
        assertThat(resolvedTypeVariable.getName()).isEqualTo("U");
        assertThat(resolvedTypeVariable.getGenericDeclaration())
                .isEqualTo(OuterTypeWithDependentInnerBound.InnerType.class);
        assertThat(resolvedTypeVariable.getBounds()).containsExactly(String.class);
    }

    private static final class OuterTypeWithDependentInnerBound<T> {
        private final class InnerType<U extends T> {}
    }
}
