/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_lettuce.lettuce_core;

import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.dynamic.support.ResolvableType;
import org.junit.jupiter.api.Test;

public class ResolvableTypeTest {
    @Test
    void createsArrayTypeFromResolvedComponent() {
        ResolvableType arrayType = ResolvableType.forArrayComponent(ResolvableType.forClass(String.class));

        assertThat(arrayType.resolve()).isEqualTo(String[].class);
        assertThat(arrayType.getComponentType().resolve()).isEqualTo(String.class);
    }
}
