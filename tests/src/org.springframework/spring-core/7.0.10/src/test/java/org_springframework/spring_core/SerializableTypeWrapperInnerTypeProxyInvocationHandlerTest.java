/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Verifies delegated scalar and type-array methods on wrapped generic types. */
public class SerializableTypeWrapperInnerTypeProxyInvocationHandlerTest {
    @Test
    void delegatesGenericTypeMethods() throws Exception {
        Field field = GenericFields.class.getDeclaredField("values");
        ResolvableType resolvableType = ResolvableType.forField(field);
        Type type = resolvableType.getType();

        assertThat(resolvableType.getGenerics())
                .extracting(ResolvableType::resolve)
                .containsExactly(String.class, Integer.class);
        assertThat(type.getTypeName()).contains("java.util.Map", "java.lang.String", "java.lang.Integer");
    }

    public static final class GenericFields {
        private Map<String, Integer> values;
    }
}
