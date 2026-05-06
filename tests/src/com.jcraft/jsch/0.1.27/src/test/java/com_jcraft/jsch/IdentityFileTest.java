/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyPair;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IdentityFileTest {
    private static final BigInteger DH_GROUP1_GENERATOR = BigInteger.valueOf(2);
    private static final BigInteger DH_GROUP1_PRIME = new BigInteger("""
            FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1
            29024E088A67CC74020BBEA63B139B22514A08798E3404DD
            EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245
            E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED
            EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381
            FFFFFFFFFFFFFFFF
            """.replaceAll("\\s", ""), 16);
    private static final String SERVER_VERSION = "SSH-2.0-IdentityFileCoverageTest";

    @TempDir
    Path temporaryDirectory;

    @Test
    void rsaIdentitySignsPublicKeyAuthenticationRequest() throws Exception {
        authenticateWithGeneratedIdentity(KeyPair.RSA, "id_rsa");
    }

    @Test
    void dsaIdentitySignsPublicKeyAuthenticationRequest() throws Exception {
        authenticateWithGeneratedIdentity(KeyPair.DSA, "id_dsa");
    }

    private void authenticateWithGeneratedIdentity(int keyType, String fileName) throws Exception {
        configureJschForDeterministicScriptedServer();
        Path privateKey = temporaryDirectory.resolve(fileName);
        writeIdentityFile(keyType, privateKey);

        JSch jsch = new JSch();
        jsch.addIdentity(privateKey.toString());

        ScriptedSshSocketFactory socketFactory = new ScriptedSshSocketFactory();
        Session session = jsch.getSession("user", "example.test", 22);
        session.setSocketFactory(socketFactory);
        Hashtable<String, String> sessionConfig = new Hashtable<>();
        sessionConfig.put("StrictHostKeyChecking", "no");
        session.setConfig(sessionConfig);

        try {
            session.connect((int) Duration.ofSeconds(10).toMillis());
            assertThat(socketFactory.signedPublicKeyRequestReceived()).isTrue();
        } finally {
            disconnectSession(session);
        }
    }

    private static void disconnectSession(Session session) {
        if (!session.isConnected()) {
            return;
        }
        try {
            session.disconnect();
        } catch (NullPointerException exception) {
            assertThat(session.isConnected()).isFalse();
        }
    }

    private static void configureJschForDeterministicScriptedServer() {
        Hashtable<String, String> config = new Hashtable<>();
        config.put("kex", "diffie-hellman-group1-sha1");
        config.put("server_host_key", "ssh-rsa");
        config.put("cipher.c2s", "test-none");
        config.put("cipher.s2c", "test-none");
        config.put("mac.c2s", "test-mac-none");
        config.put("mac.s2c", "test-mac-none");
        config.put("test-none", "com.jcraft.jsch.CipherNone");
        config.put("test-mac-none", "com.jcraft.jsch.CipherNone");
        JSch.setConfig(config);
    }

    private static void writeIdentityFile(int keyType, Path privateKey) throws Exception {
        JSch jsch = new JSch();
        KeyPair keyPair = KeyPair.genKeyPair(jsch, keyType, 1024);
        try {
            keyPair.writePrivateKey(privateKey.toString());
            keyPair.writePublicKey(privateKey + ".pub", "identity-file-test");
        } finally {
            keyPair.dispose();
        }
    }

    private static final class ScriptedSshSocketFactory implements SocketFactory {
        private final ScriptedSshTransport transport = new ScriptedSshTransport();
        private final Socket socket = new ScriptedSocket();

        @Override
        public Socket createSocket(String host, int port) throws UnknownHostException {
            return socket;
        }

        @Override
        public InputStream getInputStream(Socket socket) {
            return transport.inputStream();
        }

        @Override
        public OutputStream getOutputStream(Socket socket) {
            return transport.outputStream();
        }

        boolean signedPublicKeyRequestReceived() {
            return transport.signedPublicKeyRequestReceived();
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

    private static final class ScriptedSshTransport {
        private final Queue<byte[]> pendingInput = new ArrayDeque<>();
        private final ByteArrayOutputStream clientBytes = new ByteArrayOutputStream();
        private final AtomicBoolean signedPublicKeyRequest = new AtomicBoolean();
        private String clientVersion;
        private byte[] clientKexInitPayload;
        private byte[] serverKexInitPayload;
        private boolean versionReceived;
        private boolean newKeysQueued;
        private boolean noneAuthenticationFailed;

        ScriptedSshTransport() {
            try {
                serverKexInitPayload = kexInitPayload();
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            pendingInput.add((SERVER_VERSION + "\r\n").getBytes(StandardCharsets.US_ASCII));
            pendingInput.add(packet(serverKexInitPayload));
        }

        InputStream inputStream() {
            return new InputStream() {
                private byte[] current = new byte[0];
                private int index;

                @Override
                public int read() {
                    byte[] one = new byte[1];
                    int count = read(one, 0, one.length);
                    return count < 0 ? -1 : one[0] & 0xff;
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    if (index >= current.length) {
                        current = pendingInput.poll();
                        index = 0;
                        if (current == null) {
                            return -1;
                        }
                    }
                    int count = Math.min(length, current.length - index);
                    System.arraycopy(current, index, buffer, offset, count);
                    index += count;
                    return count;
                }
            };
        }

        OutputStream outputStream() {
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    write(new byte[] {(byte) value}, 0, 1);
                }

                @Override
                public void write(byte[] buffer, int offset, int length) throws IOException {
                    clientBytes.write(buffer, offset, length);
                    processClientBytes();
                }
            };
        }

        boolean signedPublicKeyRequestReceived() {
            return signedPublicKeyRequest.get();
        }

        private void processClientBytes() throws IOException {
            byte[] bytes = clientBytes.toByteArray();
            int offset = 0;
            if (!versionReceived) {
                int newline = indexOf(bytes, (byte) '\n');
                if (newline < 0) {
                    return;
                }
                clientVersion = new String(bytes, 0, newline, StandardCharsets.US_ASCII)
                        .replace("\r", "");
                versionReceived = true;
                offset = newline + 1;
            }
            while (bytes.length - offset >= 5) {
                int packetLength = readUInt32(bytes, offset);
                int totalLength = Integer.BYTES + packetLength;
                if (packetLength <= 0 || packetLength > 35_000 || bytes.length - offset < totalLength) {
                    break;
                }
                int paddingLength = bytes[offset + 4] & 0xff;
                int payloadLength = packetLength - paddingLength - 1;
                byte[] payload = Arrays.copyOfRange(bytes, offset + 5, offset + 5 + payloadLength);
                processClientPacket(payload);
                offset += totalLength;
            }
            clientBytes.reset();
            clientBytes.write(bytes, offset, bytes.length - offset);
        }

        private void processClientPacket(byte[] payload) throws IOException {
            int command = payload[0] & 0xff;
            if (command == 20) {
                clientKexInitPayload = payload;
            } else if (command == 30) {
                PacketReader reader = new PacketReader(payload);
                reader.readByte();
                byte[] clientDhValue = reader.readMpint();
                pendingInput.add(packet(kexReplyPayload(clientDhValue)));
                if (!newKeysQueued) {
                    pendingInput.add(packet(new byte[] {21}));
                    newKeysQueued = true;
                }
            } else if (command == 5) {
                pendingInput.add(packet(serviceAcceptPayload()));
            } else if (command == 50) {
                processUserAuthRequest(payload);
            }
        }

        private void processUserAuthRequest(byte[] payload) throws IOException {
            PacketReader reader = new PacketReader(payload);
            reader.readByte();
            reader.readString();
            reader.readString();
            String method = new String(reader.readString(), StandardCharsets.US_ASCII);
            if (method.equals("none")) {
                noneAuthenticationFailed = true;
                pendingInput.add(packet(userAuthFailurePayload()));
                return;
            }

            assertThat(noneAuthenticationFailed).isTrue();
            boolean hasSignature = reader.readByte() != 0;
            String algorithm = new String(reader.readString(), StandardCharsets.US_ASCII);
            byte[] publicKeyBlob = reader.readString();
            if (hasSignature) {
                signedPublicKeyRequest.set(true);
                pendingInput.add(packet(new byte[] {52}));
            } else {
                pendingInput.add(packet(publicKeyOkPayload(algorithm, publicKeyBlob)));
            }
        }

        private byte[] kexReplyPayload(byte[] clientDhValue) throws IOException {
            try {
                DhExchange exchange = createDhExchange(clientDhValue);
                byte[] hostKey = createRsaHostKeyBlob(exchange.hostPublicKey());
                byte[] exchangeHash = calculateExchangeHash(
                        clientVersion.getBytes(StandardCharsets.US_ASCII),
                        SERVER_VERSION.getBytes(StandardCharsets.US_ASCII),
                        clientKexInitPayload,
                        serverKexInitPayload,
                        hostKey,
                        clientDhValue,
                        exchange.serverDhValue(),
                        exchange.sharedSecret());
                byte[] signature = signRsaExchangeHash(exchange.hostPrivateKey(), exchangeHash);

                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                payload.write(31);
                writeString(payload, hostKey);
                writeMpint(payload, exchange.serverDhValue());
                writeString(payload, signature);
                return payload.toByteArray();
            } catch (Exception exception) {
                throw new IOException(exception);
            }
        }
    }

    private static DhExchange createDhExchange(byte[] clientDhValue) throws Exception {
        java.security.KeyPair hostKeyPair = generateRsaHostKeyPair();
        BigInteger serverSecret = new BigInteger(1024, new SecureRandom())
                .mod(DH_GROUP1_PRIME.subtract(BigInteger.TWO))
                .add(BigInteger.TWO);
        BigInteger serverDhValue = DH_GROUP1_GENERATOR.modPow(serverSecret, DH_GROUP1_PRIME);
        BigInteger sharedSecret = new BigInteger(1, clientDhValue).modPow(serverSecret, DH_GROUP1_PRIME);
        return new DhExchange(
                (RSAPublicKey) hostKeyPair.getPublic(),
                hostKeyPair.getPrivate(),
                positiveBytes(serverDhValue),
                fixedLengthBytes(sharedSecret, 128));
    }

    private static java.security.KeyPair generateRsaHostKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] kexInitPayload() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(20);
        payload.write(new byte[16]);
        writeString(payload, "diffie-hellman-group1-sha1".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "test-none".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "test-none".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "test-mac-none".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "test-mac-none".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "none".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, "none".getBytes(StandardCharsets.US_ASCII));
        writeString(payload, new byte[0]);
        writeString(payload, new byte[0]);
        payload.write(0);
        writeUInt32(payload, 0);
        return payload.toByteArray();
    }

    private static byte[] createRsaHostKeyBlob(RSAPublicKey publicKey) throws Exception {
        RSAPublicKeySpec spec = KeyFactory.getInstance("RSA").getKeySpec(publicKey, RSAPublicKeySpec.class);
        ByteArrayOutputStream blob = new ByteArrayOutputStream();
        writeString(blob, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
        writeString(blob, spec.getPublicExponent().toByteArray());
        writeString(blob, spec.getModulus().toByteArray());
        return blob.toByteArray();
    }

    private static byte[] calculateExchangeHash(
            byte[] clientVersion,
            byte[] serverVersion,
            byte[] clientKexInit,
            byte[] serverKexInit,
            byte[] hostKey,
            byte[] clientDhValue,
            byte[] serverDhValue,
            byte[] sharedSecret) throws Exception {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        writeString(data, clientVersion);
        writeString(data, serverVersion);
        writeString(data, clientKexInit);
        writeString(data, serverKexInit);
        writeString(data, hostKey);
        writeMpint(data, clientDhValue);
        writeMpint(data, serverDhValue);
        writeMpint(data, sharedSecret);
        return MessageDigest.getInstance("SHA-1").digest(data.toByteArray());
    }

    private static byte[] signRsaExchangeHash(PrivateKey privateKey, byte[] exchangeHash) throws Exception {
        Signature signer = Signature.getInstance("SHA1withRSA");
        signer.initSign(privateKey);
        signer.update(exchangeHash);
        ByteArrayOutputStream signature = new ByteArrayOutputStream();
        writeString(signature, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
        writeString(signature, signer.sign());
        return signature.toByteArray();
    }

    private static byte[] serviceAcceptPayload() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(6);
        writeString(payload, "ssh-userauth".getBytes(StandardCharsets.US_ASCII));
        return payload.toByteArray();
    }

    private static byte[] userAuthFailurePayload() throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(51);
        writeString(payload, "publickey".getBytes(StandardCharsets.US_ASCII));
        payload.write(0);
        return payload.toByteArray();
    }

    private static byte[] publicKeyOkPayload(String algorithm, byte[] publicKeyBlob) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(60);
        writeString(payload, algorithm.getBytes(StandardCharsets.US_ASCII));
        writeString(payload, publicKeyBlob);
        return payload.toByteArray();
    }

    private static byte[] packet(byte[] payload) {
        int paddingLength = 8 - ((payload.length + 5) % 8);
        if (paddingLength < 4) {
            paddingLength += 8;
        }
        int packetLength = payload.length + paddingLength + 1;
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + packetLength);
        buffer.putInt(packetLength);
        buffer.put((byte) paddingLength);
        buffer.put(payload);
        buffer.put(new byte[paddingLength]);
        return buffer.array();
    }

    private static void writeString(ByteArrayOutputStream output, byte[] value) throws IOException {
        writeUInt32(output, value.length);
        output.write(value);
    }

    private static void writeMpint(ByteArrayOutputStream output, byte[] value) throws IOException {
        if (value.length > 0 && (value[0] & 0x80) != 0) {
            writeUInt32(output, value.length + 1);
            output.write(0);
        } else {
            writeUInt32(output, value.length);
        }
        output.write(value);
    }

    private static void writeUInt32(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }

    private static int readUInt32(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 24)
                | ((bytes[offset + 1] & 0xff) << 16)
                | ((bytes[offset + 2] & 0xff) << 8)
                | (bytes[offset + 3] & 0xff);
    }

    private static int indexOf(byte[] bytes, byte value) {
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] positiveBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return bytes;
    }

    private static byte[] fixedLengthBytes(BigInteger value, int length) {
        byte[] bytes = positiveBytes(value);
        byte[] fixed = new byte[length];
        int sourceOffset = Math.max(0, bytes.length - length);
        int copyLength = Math.min(bytes.length, length);
        System.arraycopy(bytes, sourceOffset, fixed, length - copyLength, copyLength);
        return fixed;
    }

    private record DhExchange(
            RSAPublicKey hostPublicKey,
            PrivateKey hostPrivateKey,
            byte[] serverDhValue,
            byte[] sharedSecret) {
    }

    private static final class PacketReader {
        private final byte[] payload;
        private int offset;

        PacketReader(byte[] payload) {
            this.payload = payload;
        }

        int readByte() {
            return payload[offset++] & 0xff;
        }

        byte[] readString() throws IOException {
            int length = readUInt32(payload, offset);
            offset += Integer.BYTES;
            if (length < 0 || offset + length > payload.length) {
                throw new IOException("invalid SSH string length");
            }
            byte[] value = Arrays.copyOfRange(payload, offset, offset + length);
            offset += length;
            return value;
        }

        byte[] readMpint() throws IOException {
            return readString();
        }
    }
}
