/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

public class ObjectToArrayConverterTest {

    private final ConversionService conversionService = new DefaultConversionService();

    @Test
    void convertsScalarObjectToSingleElementTypedArray() {
        Integer source = 42;

        Long[] converted = conversionService.convert(source, Long[].class);

        assertThat(converted).containsExactly(42L);
    }
}
