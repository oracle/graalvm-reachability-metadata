/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.cli.converters.Converter;
import io.vertx.core.cli.converters.Converters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ConvertersTest {

    @Test
    void newInstanceCreatesConverterWithPublicNoArgumentConstructor() {
        Converter<String> converter = Converters.newInstance(PrefixConverter.class);

        assertEquals(PrefixConverter.class, converter.getClass());
        assertEquals("converted-input", converter.fromString("input"));
    }

    public static final class PrefixConverter implements Converter<String> {
        public PrefixConverter() {
        }

        @Override
        public String fromString(String value) {
            return "converted-" + value;
        }
    }
}
