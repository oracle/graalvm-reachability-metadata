/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.DH;
import com.jcraft.jsch.HASH;
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
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class DHGEXTest {
    private static final String GROUP_EXCHANGE_KEX = "diffie-hellman-group-exchange-sha256";
    private static final AtomicBoolean HASH_INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean DH_INITIALIZED = new AtomicBoolean();

    @Test
    void checkKexesInitializesGroupExchangeKeyExchange() throws JSchException {
        HASH_INITIALIZED.set(false);
        DH_INITIALIZED.set(false);

        Session session = new JSch().getSession("coverage", "example.test", 22);
        session.setSocketFactory(new ServerVersionOnlySocketFactory());
        configureGroupExchangeAvailabilityCheck(session);

        assertThatThrownBy(() -> session.connect(1_000)).isInstanceOf(JSchException.class);

        assertThat(HASH_INITIALIZED.get()).isTrue();
        assertThat(DH_INITIALIZED.get()).isTrue();
        session.disconnect();
    }

    private static void configureGroupExchangeAvailabilityCheck(Session session) {
        session.setConfig("CheckCiphers", "");
        session.setConfig("CheckMacs", "");
        session.setConfig("CheckSignatures", "");
        session.setConfig("CheckKexes", GROUP_EXCHANGE_KEX);
        session.setConfig("kex", GROUP_EXCHANGE_KEX);
        session.setConfig("sha-256", DeterministicHASH.class.getName());
        session.setConfig("dh", DeterministicDH.class.getName());
        session.setConfig("dhgex_min", "8");
        session.setConfig("dhgex_preferred", "16");
        session.setConfig("dhgex_max", "32");
        session.setConfig("enable_server_sig_algs", "no");
        session.setConfig("enable_ext_info_in_auth", "no");
        session.setConfig("enable_strict_kex", "no");
        session.setConfig("require_strict_kex", "no");
    }

    public static final class DeterministicHASH implements HASH {
        @Override
        public void init() {
            HASH_INITIALIZED.set(true);
        }

        @Override
        public int getBlockSize() {
            return 32;
        }

        @Override
        public void update(byte[] foo, int start, int len) {
        }

        @Override
        public byte[] digest() {
            return new byte[getBlockSize()];
        }
    }

    public static final class DeterministicDH implements DH {
        @Override
        public void init() {
            DH_INITIALIZED.set(true);
        }

        @Override
        public void setP(byte[] p) {
        }

        @Override
        public void setG(byte[] g) {
        }

        @Override
        public byte[] getE() {
            return new byte[] {1};
        }

        @Override
        public void setF(byte[] f) {
        }

        @Override
        public byte[] getK() {
            return new byte[] {1};
        }

        @Override
        public void checkRange() {
        }
    }

    private static final class ServerVersionOnlySocketFactory implements SocketFactory {
        private final Socket socket = new ScriptedSocket();
        private final InputStream inputStream = new ByteArrayInputStream(
                "SSH-2.0-jsch-dhgex-coverage\r\n".getBytes(StandardCharsets.US_ASCII));
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
