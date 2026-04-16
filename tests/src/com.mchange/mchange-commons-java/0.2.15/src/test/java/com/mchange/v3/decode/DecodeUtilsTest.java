/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.v3.decode;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DecodeUtilsTest {
    @Test
    void decodeUsesScalaDecoderFinderLoadedDuringClassInitialization() throws Exception {
        ScalaEncodedValue encoded = new ScalaEncodedValue("alpha");

        Object decoded = DecodeUtils.decode(encoded);

        assertThat(decoded).isEqualTo("ALPHA");
    }

    @Test
    void decodeUsesConfiguredDecoderClassFromMap() throws Exception {
        Map<String, Object> encoded = new LinkedHashMap<>();
        encoded.put(DecodeUtils.DECODER_CLASS_DOT_KEY, MapValueDecoder.class.getName());
        encoded.put("value", "beta");

        Object decoded = DecodeUtils.decode(encoded);

        assertThat(decoded).isEqualTo("decoded:beta");
    }

    public static final class ScalaEncodedValue {
        private final String value;

        public ScalaEncodedValue(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public static final class ScalaValueDecoder implements Decoder {
        @Override
        public Object decode(Object obj) {
            ScalaEncodedValue encoded = (ScalaEncodedValue) obj;
            return encoded.getValue().toUpperCase(Locale.ROOT);
        }
    }

    public static final class MapValueDecoder implements Decoder {
        @Override
        public Object decode(Object obj) {
            Map<?, ?> encoded = (Map<?, ?>) obj;
            return "decoded:" + encoded.get("value");
        }
    }
}
