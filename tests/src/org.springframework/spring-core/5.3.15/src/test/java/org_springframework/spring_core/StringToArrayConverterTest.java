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

/** Verifies delimited string-to-array conversion. */
public class StringToArrayConverterTest {
    @Test
    void splitsAndConvertsDelimitedValues() {
        DefaultConversionService service = new DefaultConversionService();

        Integer[] result = service.convert("2, 4, 8", Integer[].class);

        assertThat(result).containsExactly(2, 4, 8);
    }
}
