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
    void resolvesLambdaMethodReferenceArgumentsFromConstantPool() {
        TypeResolver.disableCache();
        try {
            Transformer<String, Integer> transformer = Integer::valueOf;

            assertThat(transformer.transform("42")).isEqualTo(42);
            assertThat(TypeResolver.resolveRawArguments(Transformer.class, transformer.getClass()))
                .containsExactly(String.class, Integer.class);
        } finally {
            TypeResolver.enableCache();
        }
    }

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

    @FunctionalInterface
    private interface Transformer<S, D> {
        D transform(S source);
    }

    private interface ArrayValue<T> {
    }

    private static class GenericArrayValue<T> implements ArrayValue<T[]> {
    }

    private static class StringArrayValue extends GenericArrayValue<String> {
    }
}
