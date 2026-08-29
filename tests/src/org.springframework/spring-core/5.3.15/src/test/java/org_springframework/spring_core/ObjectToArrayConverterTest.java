/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.support.DefaultConversionService;

/** Verifies scalar-to-array conversion. */
public class ObjectToArrayConverterTest {
    @Test
    void wrapsConvertedScalarInTypedArray() {
        DefaultConversionService service = new DefaultConversionService();

        Long[] result = service.convert(42, Long[].class);

        assertThat(result).containsExactly(42L);
    }
}
