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

/** Verifies scalar-to-array conversion with a registered element converter. */
public class ObjectToArrayConverterTest {
    @Test
    void convertsScalarIntoSingleElementArray() {
        DefaultConversionService service = new DefaultConversionService();
        service.addConverter(Scalar.class, Integer.class, source -> source.value);

        Integer[] converted = service.convert(new Scalar(7), Integer[].class);

        assertThat(converted).containsExactly(7);
    }

    public static final class Scalar {
        private final int value;

        public Scalar(int value) {
            this.value = value;
        }
    }
}
