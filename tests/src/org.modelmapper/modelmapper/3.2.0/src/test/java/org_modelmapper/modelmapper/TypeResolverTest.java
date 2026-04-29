/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_modelmapper.modelmapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import org.junit.jupiter.api.Test;
import org.modelmapper.internal.typetools.TypeResolver;

public class TypeResolverTest {
    @Test
    void resolvesRawGenericArrayArgument() {
        assertThat(TypeResolver.resolveRawArguments(ArrayValue.class, StringArrayValue.class))
            .containsExactly(String[].class);
    }

    @Test
    void reifiesGenericArrayArgument() {
        Type reifiedType = TypeResolver.reify(ArrayValue.class, StringArrayValue.class);

        assertThat(reifiedType).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) reifiedType;
        assertThat(parameterizedType.getActualTypeArguments()).containsExactly(String[].class);
    }


    private interface ArrayValue<T> {
    }

    private static class GenericArrayValue<T> implements ArrayValue<T[]> {
    }

    private static class StringArrayValue extends GenericArrayValue<String> {
    }
}
