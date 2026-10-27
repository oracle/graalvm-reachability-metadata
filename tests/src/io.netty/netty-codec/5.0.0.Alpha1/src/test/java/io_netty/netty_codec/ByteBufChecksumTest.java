/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec;

import io.netty.handler.codec.compression.JdkZlibDecoder;
import io.netty.handler.codec.compression.ZlibWrapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

public class ByteBufChecksumTest {
    @Test
    void gzipDecoderInitializesChecksumSupport() {
        JdkZlibDecoder decoder = new JdkZlibDecoder(ZlibWrapper.GZIP);

        assertFalse(decoder.isClosed());
    }
}
