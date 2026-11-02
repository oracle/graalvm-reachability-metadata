/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.config.plugins.convert.Base64Converter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class Base64ConverterTest {
    @Test
    void decodesConfiguredBinaryValues() {
        assertArrayEquals("log message".getBytes(StandardCharsets.UTF_8),
                Base64Converter.parseBase64Binary("bG9nIG1lc3NhZ2U="));
        assertArrayEquals(new byte[]{0, 1, 2, (byte) 255}, Base64Converter.parseBase64Binary("AAEC/w=="));
    }
}
