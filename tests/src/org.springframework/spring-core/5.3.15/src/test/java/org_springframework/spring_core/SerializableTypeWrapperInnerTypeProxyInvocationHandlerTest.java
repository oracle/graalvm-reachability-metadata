/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Verifies delegated operations on wrapped generic types. */
public class SerializableTypeWrapperInnerTypeProxyInvocationHandlerTest {
    @Test
    void delegatesGenericTypeArrayAndStringOperations() throws Exception {
        Field field = GenericFields.class.getDeclaredField("entries");
        ResolvableType type = ResolvableType.forField(field);

        ResolvableType[] generics = type.getGenerics();

        assertThat(generics).extracting(ResolvableType::resolve)
                .containsExactly(String.class, Integer.class);
        assertThat(type.toString()).contains("java.util.Map", "java.lang.String", "java.lang.Integer");
    }

    private static final class GenericFields {
        private Map<String, Integer> entries;
    }
}
