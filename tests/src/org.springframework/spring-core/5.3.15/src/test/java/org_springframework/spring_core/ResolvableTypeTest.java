/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Verifies resolution and construction of generic array types. */
public class ResolvableTypeTest {
    @Test
    void resolvesGenericArrayAndCreatesArrayType() throws Exception {
        Method method = GenericArrays.class.getMethod("values");
        ResolvableType genericArray = ResolvableType.forMethodReturnType(method, StringArrays.class);
        ResolvableType explicitArray = ResolvableType.forArrayComponent(ResolvableType.forClass(String.class));

        assertThat(genericArray.resolve()).isEqualTo(String[].class);
        assertThat(explicitArray.resolve()).isEqualTo(String[].class);
        assertThat(explicitArray.getComponentType().resolve()).isEqualTo(String.class);
    }

    public interface GenericArrays<T> {
        T[] values();
    }

    public static final class StringArrays implements GenericArrays<String> {
        @Override
        public String[] values() {
            return new String[] {"spring"};
        }
    }
}
