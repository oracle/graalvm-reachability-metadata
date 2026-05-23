/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.Buffer;
import com.jcraft.jsch.DHGEX;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.jce.DH;
import com.jcraft.jsch.jce.SHA1;
import com.jcraft.jsch.jce.SignatureRSA;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

public class DHGEXTest {
    private static final BigInteger GROUP_EXCHANGE_P = new BigInteger("""
            FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08
            8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B
            302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9
            A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6
            49286651ECE65381FFFFFFFFFFFFFFFF
            """.replaceAll("\\s", ""), 16);
    private static final BigInteger GROUP_EXCHANGE_G = BigInteger.valueOf(2L);
    private static final int SSH_MSG_KEX_DH_GEX_GROUP = 31;
    private static final int SSH_MSG_KEX_DH_GEX_REPLY = 33;

    @Test
    void verifiesGroupExchangeReplyWithConfiguredRsaSignatureImplementation() throws Exception {
        keepConfiguredImplementationsReachable();
        initializePacketRandomThroughSessionConnect();
        Session session = configuredSession();
        DHGEX keyExchange = new DHGEX();

        keyExchange.init(
                session,
                "SSH-2.0-server".getBytes(StandardCharsets.UTF_8),
                "SSH-2.0-client".getBytes(StandardCharsets.UTF_8),
                kexInitPayload(),
                kexInitPayload());

        assertThat(keyExchange.getState()).isEqualTo(SSH_MSG_KEX_DH_GEX_GROUP);
        assertThat(keyExchange.next(groupExchangeParameters())).isTrue();
        assertThat(keyExchange.getState()).isEqualTo(SSH_MSG_KEX_DH_GEX_REPLY);
        assertThat(keyExchange.next(groupExchangeReplyWithMismatchedSignature())).isFalse();
    }

    private static void keepConfiguredImplementationsReachable() {
        assertThat(new SHA1()).isNotNull();
        assertThat(new DH()).isNotNull();
        assertThat(new SignatureRSA()).isNotNull();
    }

    private static Session configuredSession() throws JSchException {
        Session session = new JSch().getSession("dhgex-coverage", "127.0.0.1", 22);
        Properties sessionConfig = new Properties();
        sessionConfig.setProperty("StrictHostKeyChecking", "no");
        sessionConfig.setProperty("sha-1", SHA1.class.getName());
        sessionConfig.setProperty("dh", DH.class.getName());
        sessionConfig.setProperty("signature.rsa", SignatureRSA.class.getName());
        session.setConfig(sessionConfig);
        return session;
    }

    private static void initializePacketRandomThroughSessionConnect() throws Exception {
        try (ClosingServer server = new ClosingServer()) {
            Session session = new JSch().getSession("dhgex-coverage", "127.0.0.1", server.port());
            assertThatThrownBy(() -> session.connect(10_000))
                    .isInstanceOf(JSchException.class);
            session.disconnect();
        }
    }

    private static byte[] kexInitPayload() {
        PacketBuilder builder = new PacketBuilder();
        builder.putByte((byte) 20);
        builder.putBytes(new byte[16]);
        builder.putString("diffie-hellman-group-exchange-sha1".getBytes(StandardCharsets.UTF_8));
        builder.putString("ssh-rsa".getBytes(StandardCharsets.UTF_8));
        builder.putString("3des-cbc".getBytes(StandardCharsets.UTF_8));
        builder.putString("3des-cbc".getBytes(StandardCharsets.UTF_8));
        builder.putString("hmac-sha1".getBytes(StandardCharsets.UTF_8));
        builder.putString("hmac-sha1".getBytes(StandardCharsets.UTF_8));
        builder.putString("none".getBytes(StandardCharsets.UTF_8));
        builder.putString("none".getBytes(StandardCharsets.UTF_8));
        builder.putString(new byte[0]);
        builder.putString(new byte[0]);
        builder.putByte((byte) 0);
        builder.putInt(0);
        return builder.toByteArray();
    }

    private static Buffer groupExchangeParameters() {
        PacketBuilder payload = new PacketBuilder();
        payload.putByte((byte) SSH_MSG_KEX_DH_GEX_GROUP);
        payload.putMpInt(GROUP_EXCHANGE_P);
        payload.putMpInt(GROUP_EXCHANGE_G);
        return new Buffer(wrapPacket(payload.toByteArray()));
    }

    private static Buffer groupExchangeReplyWithMismatchedSignature() throws Exception {
        RSAPublicKey hostPublicKey = (RSAPublicKey) rsaKeyPair().getPublic();
        BigInteger privateExponent = new BigInteger(1024, new SecureRandom())
                .mod(GROUP_EXCHANGE_P.subtract(BigInteger.valueOf(3L)))
                .add(BigInteger.TWO);
        BigInteger serverPublic = GROUP_EXCHANGE_G.modPow(privateExponent, GROUP_EXCHANGE_P);

        PacketBuilder payload = new PacketBuilder();
        payload.putByte((byte) SSH_MSG_KEX_DH_GEX_REPLY);
        payload.putString(hostKeyBlob(hostPublicKey));
        payload.putMpInt(serverPublic);
        payload.putString(mismatchedRsaSignatureBlob());
        return new Buffer(wrapPacket(payload.toByteArray()));
    }

    private static byte[] hostKeyBlob(RSAPublicKey key) {
        PacketBuilder builder = new PacketBuilder();
        builder.putString("ssh-rsa".getBytes(StandardCharsets.UTF_8));
        builder.putMpInt(key.getPublicExponent());
        builder.putMpInt(key.getModulus());
        return builder.toByteArray();
    }

    private static byte[] mismatchedRsaSignatureBlob() {
        PacketBuilder builder = new PacketBuilder();
        builder.putString("ssh-rsa".getBytes(StandardCharsets.UTF_8));
        builder.putString(new byte[128]);
        return builder.toByteArray();
    }

    private static java.security.KeyPair rsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return keyPairGenerator.generateKeyPair();
    }

    private static byte[] wrapPacket(byte[] payload) {
        int paddingLength = 8 - ((payload.length + 5) % 8);
        if (paddingLength < 4) {
            paddingLength += 8;
        }
        PacketBuilder builder = new PacketBuilder();
        builder.putInt(payload.length + paddingLength + 1);
        builder.putByte((byte) paddingLength);
        builder.putBytes(payload);
        builder.putBytes(new byte[paddingLength]);
        return builder.toByteArray();
    }

    private static final class ClosingServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final Future<?> serverTask;

        ClosingServer() throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            executorService = Executors.newSingleThreadExecutor();
            serverTask = executorService.submit(this::acceptAndClose);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            closeQuietly(serverSocket);
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("SSH test server did not stop");
            }
            serverTask.get(5, TimeUnit.SECONDS);
        }

        private void acceptAndClose() {
            try (Socket socket = serverSocket.accept()) {
                socket.setSoTimeout(10_000);
            } catch (IOException exception) {
                if (!serverSocket.isClosed()) {
                    throw new AssertionError(exception);
                }
            }
        }

        private static void closeQuietly(Closeable closeable) {
            try {
                closeable.close();
            } catch (IOException ignored) {
                // Best-effort cleanup after bounded packet-random initialization.
            }
        }
    }

    private static final class PacketBuilder {
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        void putByte(byte value) {
            outputStream.write(value);
        }

        void putBytes(byte[] value) {
            outputStream.writeBytes(value);
        }

        void putInt(int value) {
            outputStream.write((value >>> 24) & 0xff);
            outputStream.write((value >>> 16) & 0xff);
            outputStream.write((value >>> 8) & 0xff);
            outputStream.write(value & 0xff);
        }

        void putString(byte[] value) {
            putInt(value.length);
            putBytes(value);
        }

        void putMpInt(BigInteger value) {
            byte[] bytes = value.toByteArray();
            if ((bytes[0] & 0x80) != 0) {
                putInt(bytes.length + 1);
                putByte((byte) 0);
                putBytes(bytes);
            } else {
                putString(bytes);
            }
        }

        byte[] toByteArray() {
            return outputStream.toByteArray();
        }
    }
}
