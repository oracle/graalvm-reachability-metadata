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
import com.jcraft.jsch.KEM;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class DHECNKEMTest {
    private static final String HYBRID_EC_KEX = "mlkem768nistp256-sha256";
    private static final int MLKEM768_PUBLIC_KEY_LENGTH = 1_184;
    private static final AtomicBoolean KEM_INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean KEM_PUBLIC_KEY_REQUESTED = new AtomicBoolean();

    @Test
    void checkKexesInitializesHybridEcKemKeyExchange() throws JSchException {
        KEM_INITIALIZED.set(false);
        KEM_PUBLIC_KEY_REQUESTED.set(false);

        Session session = new JSch().getSession("coverage", "example.test", 22);
        session.setSocketFactory(new ServerVersionOnlySocketFactory());
        configureHybridEcKemAvailabilityCheck(session);

        assertThatThrownBy(() -> session.connect(1_000)).isInstanceOf(JSchException.class);

        assertThat(KEM_INITIALIZED.get()).isTrue();
        assertThat(KEM_PUBLIC_KEY_REQUESTED.get()).isTrue();
        session.disconnect();
    }

    private static void configureHybridEcKemAvailabilityCheck(Session session) {
        session.setConfig("CheckCiphers", "");
        session.setConfig("CheckMacs", "");
        session.setConfig("CheckSignatures", "");
        session.setConfig("CheckKexes", HYBRID_EC_KEX);
        session.setConfig("kex", HYBRID_EC_KEX);
        session.setConfig("mlkem768", DeterministicKEM.class.getName());
        session.setConfig("enable_server_sig_algs", "no");
        session.setConfig("enable_ext_info_in_auth", "no");
        session.setConfig("enable_strict_kex", "no");
        session.setConfig("require_strict_kex", "no");
    }

    public static final class DeterministicKEM implements KEM {
        @Override
        public void init() {
            KEM_INITIALIZED.set(true);
        }

        @Override
        public byte[] getPublicKey() {
            KEM_PUBLIC_KEY_REQUESTED.set(true);
            byte[] publicKey = new byte[MLKEM768_PUBLIC_KEY_LENGTH];
            Arrays.fill(publicKey, (byte) 7);
            return publicKey;
        }

        @Override
        public byte[] decapsulate(byte[] encapsulation) {
            return new byte[32];
        }
    }

    private static final class ServerVersionOnlySocketFactory implements SocketFactory {
        private final Socket socket = new ScriptedSocket();
        private final InputStream inputStream = new ByteArrayInputStream(
                "SSH-2.0-jsch-dhecnkem-coverage\r\n".getBytes(StandardCharsets.US_ASCII));
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
