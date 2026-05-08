/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.cli.converters.Converter;
import io.vertx.core.cli.converters.ValueOfBasedConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ValueOfBasedConverterTest {

    @Test
    void getIfEligibleCreatesConverterThatUsesStaticValueOfMethod() {
        Converter<ValueOfValue> converter = ValueOfBasedConverter.getIfEligible(ValueOfValue.class);

        assertNotNull(converter);
        ValueOfValue value = converter.fromString("configured-value");

        assertEquals("configured-value", value.value());
    }

    public static final class ValueOfValue {
        private final String value;

        private ValueOfValue(String value) {
            this.value = value;
        }

        public static ValueOfValue valueOf(String value) {
            return new ValueOfValue(value);
        }

        String value() {
            return value;
        }
    }
}
