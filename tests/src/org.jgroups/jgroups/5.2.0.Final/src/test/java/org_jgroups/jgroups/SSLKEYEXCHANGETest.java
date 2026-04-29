/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.SSL_KEY_EXCHANGE;
import org.jgroups.util.SslContextFactory;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLKEYEXCHANGETest {
    @Test
    void createsConfiguredSessionVerifierDuringInitialization() throws Exception {
        RecordingSessionVerifier.reset();
        SSLContext sslContext = SSLContext.getInstance(SslContextFactory.getDefaultSslProtocol());
        sslContext.init(null, null, null);
        SSL_KEY_EXCHANGE keyExchange = new SSL_KEY_EXCHANGE()
            .setClientSSLContext(sslContext)
            .setServerSSLContext(sslContext)
            .setSessionVerifierClass(RecordingSessionVerifier.class.getName())
            .setSessionVerifierArg("trusted-peer");

        keyExchange.init();

        assertThat(keyExchange.getSessionVerifier()).isInstanceOf(RecordingSessionVerifier.class);
        assertThat(RecordingSessionVerifier.lastArgument()).isEqualTo("trusted-peer");
    }

    public static class RecordingSessionVerifier implements SSL_KEY_EXCHANGE.SessionVerifier {
        private static final AtomicReference<String> LAST_ARGUMENT = new AtomicReference<>();

        public static void reset() {
            LAST_ARGUMENT.set(null);
        }

        public static String lastArgument() {
            return LAST_ARGUMENT.get();
        }

        @Override
        public void init(String arg) {
            LAST_ARGUMENT.set(arg);
        }

        @Override
        public void verify(SSLSession session) {
        }
    }
}
