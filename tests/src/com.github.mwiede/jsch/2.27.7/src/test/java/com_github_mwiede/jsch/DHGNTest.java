/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class DHGNTest {
    private static final String FIXED_GROUP_KEX = "diffie-hellman-group14-sha256";

    @Test
    void checkKexesInitializesFixedGroupKeyExchange() throws JSchException {
        Session session = new JSch().getSession("coverage", "example.test", 22);
        session.setSocketFactory(new ServerVersionOnlySocketFactory());
        configureFixedGroupAvailabilityCheck(session);

        assertThatThrownBy(() -> session.connect(1_000))
                .isInstanceOf(JSchException.class)
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain("available kexes"));

        session.disconnect();
    }

    private static void configureFixedGroupAvailabilityCheck(Session session) {
        session.setConfig("CheckCiphers", "");
        session.setConfig("CheckMacs", "");
        session.setConfig("CheckSignatures", "");
        session.setConfig("CheckKexes", FIXED_GROUP_KEX);
        session.setConfig("kex", FIXED_GROUP_KEX);
        session.setConfig("enable_server_sig_algs", "no");
        session.setConfig("enable_ext_info_in_auth", "no");
        session.setConfig("enable_strict_kex", "no");
        session.setConfig("require_strict_kex", "no");
    }

    private static final class ServerVersionOnlySocketFactory implements SocketFactory {
        private final Socket socket = new ScriptedSocket();
        private final InputStream inputStream = new ByteArrayInputStream(
                "SSH-2.0-jsch-dhgn-coverage\r\n".getBytes(StandardCharsets.US_ASCII));
        private final OutputStream outputStream = new ByteArrayOutputStream();

        @Override
        public Socket createSocket(String host, int port) {
            return socket;
        }

        @Override
        public InputStream getInputStream(Socket socket) {
            return inputStream;
        }

        @Override
        public OutputStream getOutputStream(Socket socket) {
            return outputStream;
        }
    }

    private static final class ScriptedSocket extends Socket {
        @Override
        public void setTcpNoDelay(boolean on) {
        }

        @Override
        public synchronized void setSoTimeout(int timeout) {
        }

        @Override
        public synchronized void close() {
        }
    }
}
