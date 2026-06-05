/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ResolvableType;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ResolvableTypeTest {

    @Test
    void arrayComponentFactoryRetainsGenericComponentInformation() {
        ResolvableType listOfStrings = ResolvableType.forClassWithGenerics(List.class, String.class);

        ResolvableType arrayType = ResolvableType.forArrayComponent(listOfStrings);

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.resolve()).isEqualTo(List[].class);
        assertThat(arrayType.getComponentType().resolve()).isEqualTo(List.class);
        assertThat(arrayType.getComponentType().resolveGeneric()).isEqualTo(String.class);
    }

    @Test
    void methodReturnTypeResolvesGenericArraysAgainstImplementationClass() throws NoSuchMethodException {
        Method method = GenericArrayOperations.class.getMethod("findAll");

        ResolvableType returnType = ResolvableType.forMethodReturnType(method, StringArrayOperations.class);

        assertThat(returnType.isArray()).isTrue();
        assertThat(returnType.resolve()).isEqualTo(String[].class);
        assertThat(returnType.getComponentType().resolve()).isEqualTo(String.class);
    }

    public abstract static class GenericArrayOperations<T> {

        public abstract T[] findAll();
    }

    public abstract static class StringArrayOperations extends GenericArrayOperations<String> {
    }
}
