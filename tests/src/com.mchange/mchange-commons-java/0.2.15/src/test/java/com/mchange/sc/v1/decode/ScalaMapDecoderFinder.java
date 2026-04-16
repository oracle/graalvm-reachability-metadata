/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.mchange.sc.v1.decode;

import com.mchange.v3.decode.CannotDecodeException;
import com.mchange.v3.decode.DecodeUtilsTest;
import com.mchange.v3.decode.DecoderFinder;

public final class ScalaMapDecoderFinder implements DecoderFinder {
    public ScalaMapDecoderFinder() {
    }

    @Override
    public String decoderClassName(Object encoded) throws CannotDecodeException {
        if (encoded instanceof DecodeUtilsTest.ScalaEncodedValue) {
            return DecodeUtilsTest.ScalaValueDecoder.class.getName();
        }
        return null;
    }
}
