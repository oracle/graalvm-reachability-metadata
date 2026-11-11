/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import javax.net.ssl.SSLEngine;

import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleUtil;

final class BouncyCastleEngineSupport {
    private BouncyCastleEngineSupport() {
    }

    static SSLEngine engine(boolean server) throws Exception {
        return TlsTestSupport.context(server, SslProvider.JDK, BouncyCastleUtil.getBcProviderJsse())
                .newEngine(ByteBufAllocator.DEFAULT);
    }
}
