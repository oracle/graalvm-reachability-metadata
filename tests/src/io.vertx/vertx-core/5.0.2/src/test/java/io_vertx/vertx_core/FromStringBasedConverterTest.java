/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.cli.converters.Converter;
import io.vertx.core.cli.converters.FromStringBasedConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FromStringBasedConverterTest {

    @Test
    void getIfEligibleCreatesConverterThatUsesStaticFromStringMethod() {
        Converter<FromStringValue> converter = FromStringBasedConverter.getIfEligible(FromStringValue.class);

        assertNotNull(converter);
        FromStringValue value = converter.fromString("configured-value");

        assertEquals("configured-value", value.value());
    }

    public static final class FromStringValue {
        private final String value;

        private FromStringValue(String value) {
            this.value = value;
        }

        public static FromStringValue fromString(String value) {
            return new FromStringValue(value);
        }

        String value() {
            return value;
        }
    }
}
