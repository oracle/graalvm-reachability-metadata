/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ResolvableType;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ResolvableTypeTest {

    @Test
    void resolvesGenericArrayTypeToArrayClass() {
        ResolvableType arrayType = ResolvableType.forType(new ListOfStringArrayType());

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.resolve()).isEqualTo(List[].class);
        assertThat(arrayType.getComponentType().resolve()).isEqualTo(List.class);
        assertThat(arrayType.getComponentType().getGeneric(0).resolve()).isEqualTo(String.class);
    }

    @Test
    void createsArrayTypeFromResolvableComponent() {
        ResolvableType componentType = ResolvableType.forClassWithGenerics(List.class, String.class);

        ResolvableType arrayType = ResolvableType.forArrayComponent(componentType);

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.resolve()).isEqualTo(List[].class);
        assertThat(arrayType.getComponentType()).isEqualTo(componentType);
        assertThat(arrayType.getComponentType().getGeneric(0).resolve()).isEqualTo(String.class);
    }

    private static final class ListOfStringArrayType implements GenericArrayType {
        private final Type componentType = ResolvableType.forClassWithGenerics(List.class, String.class).getType();

        @Override
        public Type getGenericComponentType() {
            return componentType;
        }
    }
}
