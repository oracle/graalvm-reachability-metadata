/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleUtil;

public class BouncyCastleAlpnSslUtilsTest {
    @Test
    void bouncyCastleNegotiatesHttp2AndTransfersEncryptedData() throws Exception {
        TlsTestSupport.exchange(SslProvider.JDK, BouncyCastleUtil.getBcProviderJsse());
    }
}
