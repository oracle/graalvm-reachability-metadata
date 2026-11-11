/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.SecureRandom;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.handler.ssl.util.BouncyCastleUtil;

public class BouncyCastleUtilAnonymous1Test {
    @Test
    void discoveredProvidersCreateTlsEngines() throws Exception {
        assertTrue(BouncyCastleUtil.isBcProvAvailable());
        assertTrue(BouncyCastleUtil.isBcPkixAvailable());
        assertTrue(BouncyCastleUtil.isBcTlsAvailable());
        SSLContext context = SSLContext.getInstance("TLS", BouncyCastleUtil.getBcProviderJsse());
        context.init(null, null, new SecureRandom());
        SSLEngine engine = context.createSSLEngine();
        assertTrue(BouncyCastleUtil.isBcJsseInUse(engine));
        assertTrue(engine.getSupportedCipherSuites().length > 0);
    }
}
