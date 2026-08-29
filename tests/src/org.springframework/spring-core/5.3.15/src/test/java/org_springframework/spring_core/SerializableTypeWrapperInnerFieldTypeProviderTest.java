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
import org.springframework.util.SerializationUtils;

/** Verifies restoration of generic field type information after serialization. */
public class SerializableTypeWrapperInnerFieldTypeProviderTest {
    @Test
    void restoresGenericField() throws Exception {
        Field field = GenericFields.class.getDeclaredField("names");
        ResolvableType original = ResolvableType.forField(field);

        ResolvableType restored = roundTrip(original);

        assertThat(restored.resolve()).isEqualTo(List.class);
        assertThat(restored.resolveGeneric()).isEqualTo(String.class);
    }

    private static ResolvableType roundTrip(ResolvableType type) {
        return (ResolvableType) SerializationUtils.deserialize(SerializationUtils.serialize(type));
    }

    private static final class GenericFields {
        private List<String> names;
    }
}
