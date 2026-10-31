/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import java.io.IOException;

import org.junit.jupiter.api.Test;

public class ByteBufChecksumInnerReflectiveByteBufChecksumTest {
    @Test
    void validatesGzipHeadersInADirectBuffer() throws IOException {
        ByteBufChecksumTest.verifyGzip(true, "checksummed direct payload");
    }
}
