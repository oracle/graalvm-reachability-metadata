/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_mina.mina_core;

import java.nio.charset.Charset;

import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.codec.textline.TextLineDecoder;
import org.apache.mina.filter.codec.textline.TextLineEncoder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ProtocolCodecFilterTest {
    @Test
    void requiresAPublicDefaultEncoderConstructor() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProtocolCodecFilter(ConfiguredEncoder.class, TextLineDecoder.class));
    }

    @Test
    void requiresAPublicDefaultDecoderConstructor() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProtocolCodecFilter(TextLineEncoder.class, ConfiguredDecoder.class));
    }

    public static class ConfiguredEncoder extends TextLineEncoder {
        public ConfiguredEncoder(Charset charset) {
            super(charset);
        }
    }

    public static class ConfiguredDecoder extends TextLineDecoder {
        public ConfiguredDecoder(Charset charset) {
            super(charset);
        }
    }
}
