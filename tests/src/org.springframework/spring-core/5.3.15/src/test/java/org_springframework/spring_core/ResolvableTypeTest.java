/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Verifies array type creation and generic-array resolution. */
public class ResolvableTypeTest {
    @Test
    void resolvesGenericArrayAndCreatesArrayType() throws Exception {
        Field values = GenericContainer.class.getDeclaredField("values");

        ResolvableType genericArray = ResolvableType.forField(values, StringContainer.class);
        ResolvableType explicitArray = ResolvableType.forArrayComponent(ResolvableType.forClass(Integer.class));

        assertThat(genericArray.resolve()).isEqualTo(String[].class);
        assertThat(explicitArray.resolve()).isEqualTo(Integer[].class);
        assertThat(explicitArray.getComponentType().resolve()).isEqualTo(Integer.class);
    }

    public static class GenericContainer<T> {
        private T[] values;
    }

    public static final class StringContainer extends GenericContainer<String> {}
}
