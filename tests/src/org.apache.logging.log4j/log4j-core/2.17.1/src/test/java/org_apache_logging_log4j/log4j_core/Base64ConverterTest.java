/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

public class Base64ConverterTest {
    @Test
    @Timeout(20)
    void byteArrayConverterDecodesBase64Values() {
        final TypeConverters.ByteArrayConverter converter = new TypeConverters.ByteArrayConverter();

        final byte[] decoded = converter.convert("Base64:TG9nNGogY29uZmlndXJhdGlvbiB2YWx1ZQ==");

        assertThat(new String(decoded, StandardCharsets.UTF_8)).isEqualTo("Log4j configuration value");
    }
}
