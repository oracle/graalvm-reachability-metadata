/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_vertx.vertx_core;

import io.vertx.core.VertxException;
import io.vertx.core.cli.converters.ConstructorBasedConverter;
import io.vertx.core.cli.converters.Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ConstructorBasedConverterTest {

    @Test
    void getIfEligibleCreatesConverterThatUsesStringConstructor() {
        Converter<VertxException> converter = ConstructorBasedConverter.getIfEligible(VertxException.class);

        assertNotNull(converter);
        VertxException exception = converter.fromString("configured-message");

        assertEquals("configured-message", exception.getMessage());
    }
}
