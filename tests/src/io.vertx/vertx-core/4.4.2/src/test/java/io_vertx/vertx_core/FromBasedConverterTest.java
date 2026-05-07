/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.cli.converters.Converter;
import io.vertx.core.cli.converters.FromBasedConverter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class FromBasedConverterTest {

    @Test
    void getIfEligibleCreatesConverterThatUsesStaticFromMethod() {
        Converter<FromValue> converter = FromBasedConverter.getIfEligible(FromValue.class);

        assertNotNull(converter);
        FromValue value = converter.fromString("configured-value");

        assertEquals("configured-value", value.value());
    }

    public static final class FromValue {
        private final String value;

        private FromValue(String value) {
            this.value = value;
        }

        public static FromValue from(String value) {
            return new FromValue(value);
        }

        String value() {
            return value;
        }
    }
}
