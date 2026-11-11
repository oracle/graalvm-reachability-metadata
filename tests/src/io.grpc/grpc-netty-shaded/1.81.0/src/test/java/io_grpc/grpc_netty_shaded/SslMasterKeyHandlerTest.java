/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.SecretKey;
import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslMasterKeyHandler;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslProvider;

public class SslMasterKeyHandlerTest {
    @Test
    void observesTheMasterSecretOfAnEncryptedTlsSession() throws Exception {
        AtomicReference<SecretKey> masterSecret = new AtomicReference<>();
        AtomicReference<SSLSession> session = new AtomicReference<>();
        SslMasterKeyHandler observer = new SslMasterKeyHandler() {
            @Override
            protected boolean masterKeyHandlerEnabled() {
                return true;
            }

            @Override
            protected void accept(SecretKey key, SSLSession establishedSession) {
                masterSecret.set(key);
                session.set(establishedSession);
            }
        };
        TlsTestSupport.exchange(SslProvider.OPENSSL, null, observer, (server, client) -> {
            assertNotNull(masterSecret.get());
            assertEquals(48, masterSecret.get().getEncoded().length);
            assertNotNull(session.get());
            assertEquals("TLSv1.2", session.get().getProtocol());
            assertTrue(session.get().isValid());
        });
    }
}
