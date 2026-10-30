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

/** Verifies array class hierarchy traversal during converter lookup. */
public class GenericConversionServiceInnerConvertersTest {
    @Test
    void resolvesConverterAcrossArrayClassHierarchy() {
        DefaultConversionService service = new DefaultConversionService();

        Integer[] converted = service.convert(new String[] {"4", "5"}, Integer[].class);

        assertThat(converted).containsExactly(4, 5);
    }
}
