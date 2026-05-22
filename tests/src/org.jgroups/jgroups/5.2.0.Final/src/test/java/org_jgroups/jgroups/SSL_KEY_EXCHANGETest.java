/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;

import org.jgroups.protocols.SSL_KEY_EXCHANGE;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSL_KEY_EXCHANGETest {
    @Test
    void createsConfiguredSessionVerifierDuringInitialization() throws Exception {
        RecordingSessionVerifier.reset();
        SSLContext sslContext = SSLContext.getDefault();
        SSL_KEY_EXCHANGE protocol = new SSL_KEY_EXCHANGE()
                .setClientSSLContext(sslContext)
                .setServerSSLContext(sslContext)
                .setSessionVerifierClass(RecordingSessionVerifier.class.getName())
                .setSessionVerifierArg("expected-cn");

        protocol.init();

        assertThat(protocol.getSessionVerifier()).isInstanceOf(RecordingSessionVerifier.class);
        assertThat(RecordingSessionVerifier.initializedWith()).isEqualTo("expected-cn");
    }

    public static class RecordingSessionVerifier implements SSL_KEY_EXCHANGE.SessionVerifier {
        private static String initializedWith;

        static void reset() {
            initializedWith = null;
        }

        static String initializedWith() {
            return initializedWith;
        }

        @Override
        public void init(String arg) {
            initializedWith = arg;
        }

        @Override
        public void verify(SSLSession session) {
        }
    }
}
