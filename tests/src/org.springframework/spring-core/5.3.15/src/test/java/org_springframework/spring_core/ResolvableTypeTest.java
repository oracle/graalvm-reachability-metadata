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

public class ResolvableTypeTest {

    @Test
    void createsArrayResolvableTypeFromComponentType() {
        ResolvableType componentType = ResolvableType.forClass(String.class);

        ResolvableType arrayType = ResolvableType.forArrayComponent(componentType);

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.resolve()).isEqualTo(String[].class);
        assertThat(arrayType.getComponentType().resolve()).isEqualTo(String.class);
    }

    @Test
    void resolvesGenericArrayFieldToArrayClass() throws Exception {
        Field field = GenericArrayFields.class.getField("lists");

        ResolvableType arrayType = ResolvableType.forField(field);

        assertThat(arrayType.isArray()).isTrue();
        assertThat(arrayType.resolve()).isEqualTo(List[].class);
        assertThat(arrayType.getComponentType().resolve()).isEqualTo(List.class);
        assertThat(arrayType.getComponentType().getGeneric(0).resolve()).isEqualTo(String.class);
    }

    public static class GenericArrayFields {
        public List<String>[] lists;
    }
}
