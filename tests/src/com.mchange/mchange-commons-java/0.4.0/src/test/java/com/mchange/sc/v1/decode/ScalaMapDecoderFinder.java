/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.sc.v1.decode;

import com.mchange.v3.decode.CannotDecodeException;
import com.mchange.v3.decode.DecoderFinder;
import com_mchange.mchange_commons_java.DecodeUtilsTest.PrefixingDecoder;

public class ScalaMapDecoderFinder implements DecoderFinder {
    public ScalaMapDecoderFinder() {
    }

    @Override
    public String decoderClassName(Object encoded) throws CannotDecodeException {
        if (encoded instanceof String text && text.startsWith("finder:")) {
            return PrefixingDecoder.class.getName();
        }
        return null;
    }
}
