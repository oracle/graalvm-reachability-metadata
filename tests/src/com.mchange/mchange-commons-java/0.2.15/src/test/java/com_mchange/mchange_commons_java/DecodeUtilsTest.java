/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_mchange.mchange_commons_java;

import com.mchange.v3.decode.CannotDecodeException;
import com.mchange.v3.decode.DecodeUtils;
import com.mchange.v3.decode.Decoder;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class DecodeUtilsTest {
    @Test
    void decodeUsesDecoderClassDeclaredInEncodedMap() throws CannotDecodeException {
        Map<String, Object> encoded = new HashMap<>();
        encoded.put(DecodeUtils.DECODER_CLASS_DOT_KEY, PrefixingDecoder.class.getName());
        encoded.put("value", "payload");

        Object decoded = DecodeUtils.decode(encoded);

        assertThat(decoded).isEqualTo("decoded:payload");
    }

    @Test
    void decodeUsesOptionalFinderLoadedDuringStaticInitialization() throws CannotDecodeException {
        Object decoded = DecodeUtils.decode("finder:payload");

        assertThat(decoded).isEqualTo("decoded:payload");
    }

    public static class PrefixingDecoder implements Decoder {
        public PrefixingDecoder() {
        }

        @Override
        public Object decode(Object obj) throws CannotDecodeException {
            if (obj instanceof Map<?, ?> encoded) {
                return "decoded:" + encoded.get("value");
            }
            if (obj instanceof String text && text.startsWith("finder:")) {
                return "decoded:" + text.substring("finder:".length());
            }
            throw new CannotDecodeException("Unsupported encoded value: " + obj);
        }
    }
}
