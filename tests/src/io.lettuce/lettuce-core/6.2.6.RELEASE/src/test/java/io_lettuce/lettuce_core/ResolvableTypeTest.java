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
import org.junit.jupiter.api.Test;

public class ResolvableTypeTest {

    @Test
    void arrayComponentFactoryCreatesResolvedArrayType() {
        ResolvableType componentType = ResolvableType.forClass(String.class);

        ResolvableType arrayType = ResolvableType.forArrayComponent(componentType);

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.resolve()).isEqualTo(String[].class);
        assertThat(arrayType.getComponentType().resolve()).isEqualTo(String.class);
    }

    @Test
    void genericArrayReturnTypeResolvesUsingImplementationClass() throws Exception {
        Method method = GenericArrayRepository.class.getMethod("findAll");

        ResolvableType returnType = ResolvableType.forMethodReturnType(method, StringArrayRepository.class);

        assertThat(returnType.isArray()).isTrue();
        assertThat(returnType.resolve()).isEqualTo(String[].class);
        assertThat(returnType.getComponentType().resolve()).isEqualTo(String.class);
    }

    public interface GenericArrayRepository<T extends CharSequence> {

        T[] findAll();
    }

    public static final class StringArrayRepository implements GenericArrayRepository<String> {

        @Override
        public String[] findAll() {
            return new String[] {"lettuce" };
        }
    }
}
