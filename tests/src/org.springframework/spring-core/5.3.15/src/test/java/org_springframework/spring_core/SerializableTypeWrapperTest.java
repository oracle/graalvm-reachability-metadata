/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

/** Verifies public generic type resolution backed by Spring's serializable type wrapper. */
public class SerializableTypeWrapperTest {
    @Test
    void resolvesGenericFieldType() throws Exception {
        Field field = GenericFields.class.getDeclaredField("names");

        ResolvableType type = ResolvableType.forField(field);

        assertThat(type.resolve()).isEqualTo(List.class);
        assertThat(type.resolveGeneric()).isEqualTo(String.class);
    }

    public static final class GenericFields {
        private List<String> names;
    }
}
