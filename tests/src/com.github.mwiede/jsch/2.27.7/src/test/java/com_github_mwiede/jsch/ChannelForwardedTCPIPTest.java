/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.ChannelForwardedTCPIP;
import com.jcraft.jsch.ForwardedTCPIPDaemon;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.MAC;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

public class ChannelForwardedTCPIPTest {
    private static final String SERVER_VERSION = "SSH-2.0-jsch-forwarded-tcpip-test";
    private static final String KEX_ALGORITHM = "ecdh-sha2-nistp256";
    private static final String HOST_KEY_ALGORITHM = "rsa-sha2-256";
    private static final String CIPHER_ALGORITHM = "aes128-ctr";
    private static final String MAC_ALGORITHM = "test-none-mac";
    private static final String COMPRESSION_ALGORITHM = "none";
    private static final int FORWARDED_PORT = 2222;
    private static final byte[] EOF = new byte[0];

    @Test
    void remoteForwardedDaemonChannelInstantiatesConfiguredDaemon() throws Exception {
        RecordingDaemon.reset();
        ScriptedForwardingSocketFactory socketFactory = new ScriptedForwardingSocketFactory();
        Session session = new JSch().getSession("coverage", "example.test", 22);
        session.setSocketFactory(socketFactory);
        configureForwardingSession(session);

        try {
            session.connect(1_000);
            session.setPortForwardingR(FORWARDED_PORT, RecordingDaemon.class.getName(),
                    new Object[] {"forwarded"});
            socketFactory.openForwardedChannel();

            assertThat(RecordingDaemon.awaitStarted()).isTrue();
            assertThat(RecordingDaemon.arguments()).containsExactly("forwarded");
        } finally {
            session.disconnect();
            socketFactory.close();
            RecordingDaemon.reset();
        }
    }

    private static void configureForwardingSession(Session session) {
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("enable_server_sig_algs", "no");
        session.setConfig("enable_ext_info_in_auth", "no");
        session.setConfig("enable_strict_kex", "no");
        session.setConfig("require_strict_kex", "no");
        session.setConfig("kex", KEX_ALGORITHM);
        session.setConfig("server_host_key", HOST_KEY_ALGORITHM);
        session.setConfig("cipher.c2s", CIPHER_ALGORITHM);
        session.setConfig("cipher.s2c", CIPHER_ALGORITHM);
        session.setConfig("mac.c2s", MAC_ALGORITHM);
        session.setConfig("mac.s2c", MAC_ALGORITHM);
        session.setConfig("compression.c2s", COMPRESSION_ALGORITHM);
        session.setConfig("compression.s2c", COMPRESSION_ALGORITHM);
        session.setConfig("CheckCiphers", CIPHER_ALGORITHM);
        session.setConfig("CheckMacs", MAC_ALGORITHM);
        session.setConfig("CheckKexes", KEX_ALGORITHM);
        session.setConfig("CheckSignatures", HOST_KEY_ALGORITHM);
        session.setConfig("PreferredAuthentications", "none");
        session.setConfig("enable_auth_none", "yes");
        session.setConfig(MAC_ALGORITHM, NoMac.class.getName());
    }

    public static final class RecordingDaemon implements ForwardedTCPIPDaemon {
        private static CountDownLatch started = new CountDownLatch(1);
        private static Object[] arguments = new Object[0];
        private OutputStream channelOutput;

        @Override
        public void setChannel(ChannelForwardedTCPIP channel, InputStream in, OutputStream out) {
            channelOutput = out;
        }

        @Override
        public void setArg(Object[] arg) {
            arguments = arg == null ? new Object[0] : arg.clone();
            started.countDown();
        }

        @Override
        public void run() {
            if (channelOutput != null) {
                try {
                    channelOutput.close();
                } catch (IOException ex) {
                    throw new IllegalStateException(ex);
                }
            }
        }

        private static boolean awaitStarted() throws InterruptedException {
            return started.await(3, TimeUnit.SECONDS);
        }

        private static Object[] arguments() {
            return arguments.clone();
        }

        private static void reset() {
            started = new CountDownLatch(1);
            arguments = new Object[0];
        }
    }

    public static final class NoMac implements MAC {
        @Override
        public String getName() {
            return MAC_ALGORITHM;
        }

        @Override
        public int getBlockSize() {
            return 0;
        }

        @Override
        public void init(byte[] key) {
        }

        @Override
        public void update(byte[] foo, int start, int len) {
        }

        @Override
        public void update(int foo) {
        }

        @Override
        public void doFinal(byte[] buf, int offset) {
        }
    }

    private static final class ScriptedForwardingSocketFactory implements SocketFactory {
        private final ScriptedSshTransport transport = new ScriptedSshTransport();
        private final Socket socket = new ScriptedSocket(transport);

        @Override
        public Socket createSocket(String host, int port) {
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

        private void openForwardedChannel() {
            transport.openForwardedChannel();
        }

        private void close() {
            transport.close();
        }
    }

    private static final class ScriptedSocket extends Socket {
        private final ScriptedSshTransport transport;

        private ScriptedSocket(ScriptedSshTransport transport) {
            this.transport = transport;
        }

        @Override
        public void setTcpNoDelay(boolean on) {
        }

        @Override
        public synchronized void setSoTimeout(int timeout) {
        }

        @Override
        public synchronized void close() {
            transport.close();
        }
    }

    private static final class ScriptedSshTransport {
        private final BlockingQueue<byte[]> pendingInput = new LinkedBlockingQueue<>();
        private final ByteArrayOutputStream clientBytes = new ByteArrayOutputStream();
        private final KeyPair rsaHostKey;
        private String clientVersion;
        private byte[] clientKexInitPayload;
        private byte[] serverKexInitPayload;
        private boolean versionReceived;
        private boolean newKeysQueued;
        private int serverBlockSize = 8;
        private byte[] encodedSecret;
        private byte[] exchangeHash;
        private byte[] sessionId;
        private Cipher c2sCipher;
        private Cipher s2cCipher;
        private boolean closed;

        private ScriptedSshTransport() {
            pendingInput.add((SERVER_VERSION + "\r\n").getBytes(StandardCharsets.US_ASCII));
            rsaHostKey = generateRsaKeyPair();
        }

        private InputStream inputStream() {
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
                    if (length == 0) {
                        return 0;
                    }
                    try {
                        while (index >= current.length) {
                            current = pendingInput.take();
                            index = 0;
                            if (current == EOF) {
                                return -1;
                            }
                        }
                        int count = Math.min(length, current.length - index);
                        System.arraycopy(current, index, buffer, offset, count);
                        index += count;
                        return count;
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return -1;
                    }
                }
            };
        }

        private OutputStream outputStream() {
            return new OutputStream() {
                @Override
                public void write(int value) throws IOException {
                    write(new byte[] {(byte) value}, 0, 1);
                }

                @Override
                public void write(byte[] buffer, int offset, int length) throws IOException {
                    synchronized (ScriptedSshTransport.this) {
                        if (!closed) {
                            clientBytes.write(buffer, offset, length);
                            processClientBytes();
                        }
                    }
                }
            };
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
                ClientPacket clientPacket = nextClientPacket(bytes, offset);
                if (clientPacket == null) {
                    break;
                }
                processClientPacket(clientPacket.payload());
                offset += clientPacket.totalLength();
            }
            clientBytes.reset();
            clientBytes.write(bytes, offset, bytes.length - offset);
        }

        private void processClientPacket(byte[] payload) {
            int command = payload[0] & 0xff;
            if (command == 20) {
                clientKexInitPayload = payload;
                serverKexInitPayload = kexInitPayload();
                queuePacket(serverKexInitPayload);
            } else if (command == 30) {
                byte[] clientPoint = readString(payload, 1).value();
                queuePacket(ecdhReplyPayload(clientPoint));
                if (!newKeysQueued) {
                    queuePacket(new byte[] {21});
                    serverBlockSize = 16;
                    newKeysQueued = true;
                }
            } else if (command == 21) {
                activateKeys();
            } else if (command == 5) {
                queuePacket(serviceAcceptPayload(payload));
            } else if (command == 50) {
                queuePacket(new byte[] {52});
            } else if (command == 80) {
                queuePacket(new byte[] {81});
            }
        }

        private ClientPacket nextClientPacket(byte[] bytes, int offset) {
            try {
                if (c2sCipher == null) {
                    int packetLength = readUint32(bytes, offset);
                    int totalLength = 4 + packetLength;
                    if (!isCompletePacket(bytes, offset, packetLength, totalLength)) {
                        return null;
                    }
                    int paddingLength = bytes[offset + 4] & 0xff;
                    byte[] payload = clientPayload(bytes, offset, packetLength, paddingLength);
                    return new ClientPacket(payload, totalLength);
                }

                if (bytes.length - offset < serverBlockSize) {
                    return null;
                }
                byte[] firstBlock = Arrays.copyOfRange(bytes, offset, offset + serverBlockSize);
                c2sCipher.update(firstBlock, 0, firstBlock.length, firstBlock, 0);
                int packetLength = readUint32(firstBlock, 0);
                int totalLength = 4 + packetLength;
                if (!isCompletePacket(bytes, offset, packetLength, totalLength)) {
                    return null;
                }
                byte[] packetBytes = Arrays.copyOfRange(bytes, offset, offset + totalLength);
                System.arraycopy(firstBlock, 0, packetBytes, 0, firstBlock.length);
                c2sCipher.update(packetBytes, firstBlock.length, totalLength - firstBlock.length,
                        packetBytes, firstBlock.length);
                int paddingLength = packetBytes[4] & 0xff;
                return new ClientPacket(clientPayload(packetBytes, 0, packetLength, paddingLength),
                        totalLength);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private boolean isCompletePacket(byte[] bytes, int offset, int packetLength,
                int totalLength) {
            return packetLength > 0
                    && packetLength <= 35_000
                    && bytes.length - offset >= totalLength;
        }

        private byte[] clientPayload(byte[] packetBytes, int offset, int packetLength,
                int paddingLength) {
            int payloadLength = packetLength - paddingLength - 1;
            return Arrays.copyOfRange(packetBytes, offset + 5, offset + 5 + payloadLength);
        }

        private void queuePacket(byte[] payload) {
            byte[] packetBytes = packet(payload, serverBlockSize);
            if (s2cCipher != null) {
                try {
                    s2cCipher.update(packetBytes, 0, packetBytes.length, packetBytes, 0);
                } catch (GeneralSecurityException ex) {
                    throw new IllegalStateException(ex);
                }
            }
            pendingInput.add(packetBytes);
        }

        private void openForwardedChannel() {
            queuePacket(forwardedTcpipChannelOpenPayload());
        }

        private byte[] serviceAcceptPayload(byte[] serviceRequestPayload) {
            try {
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                payload.write(6);
                writeString(payload, readString(serviceRequestPayload, 1).value());
                return payload.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private byte[] forwardedTcpipChannelOpenPayload() {
            try {
                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                payload.write(90);
                writeString(payload, "forwarded-tcpip".getBytes(StandardCharsets.US_ASCII));
                writeUint32(payload, 0);
                writeUint32(payload, 32_768);
                writeUint32(payload, 16_384);
                writeString(payload, "localhost".getBytes(StandardCharsets.US_ASCII));
                writeUint32(payload, FORWARDED_PORT);
                writeString(payload, "127.0.0.1".getBytes(StandardCharsets.US_ASCII));
                writeUint32(payload, 49_152);
                return payload.toByteArray();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private byte[] ecdhReplyPayload(byte[] clientPoint) {
            try {
                KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
                ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair serverKeyPair = ecGenerator.generateKeyPair();
                ECPublicKey serverPublicKey = (ECPublicKey) serverKeyPair.getPublic();

                ECPublicKey clientPublicKey =
                        publicKeyFromPoint(clientPoint, serverPublicKey.getParams());
                byte[] sharedSecret = sharedSecret(serverKeyPair, clientPublicKey);
                byte[] hostKeyBlob = rsaHostKeyBlob();
                byte[] serverPoint = ecPoint(serverPublicKey);
                encodedSecret = mpint(sharedSecret);
                exchangeHash = exchangeHash(hostKeyBlob, clientPoint, serverPoint, encodedSecret);
                sessionId = exchangeHash.clone();
                byte[] signatureBlob = signatureBlob(exchangeHash);

                ByteArrayOutputStream payload = new ByteArrayOutputStream();
                payload.write(31);
                writeString(payload, hostKeyBlob);
                writeString(payload, serverPoint);
                writeString(payload, signatureBlob);
                return payload.toByteArray();
            } catch (GeneralSecurityException | IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private byte[] sharedSecret(KeyPair serverKeyPair, ECPublicKey clientPublicKey)
                throws GeneralSecurityException {
            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(serverKeyPair.getPrivate());
            agreement.doPhase(clientPublicKey, true);
            return normalize(agreement.generateSecret());
        }

        private ECPublicKey publicKeyFromPoint(byte[] point, ECParameterSpec params)
                throws GeneralSecurityException {
            if (point.length != 65 || point[0] != 4) {
                throw new InvalidKeySpecException("Only uncompressed P-256 points are supported");
            }
            byte[] x = Arrays.copyOfRange(point, 1, 33);
            byte[] y = Arrays.copyOfRange(point, 33, 65);
            ECPoint ecPoint = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
            return (ECPublicKey) KeyFactory.getInstance("EC")
                    .generatePublic(new ECPublicKeySpec(ecPoint, params));
        }

        private byte[] exchangeHash(
                byte[] hostKeyBlob, byte[] clientPoint, byte[] serverPoint, byte[] encodedSecret)
                throws GeneralSecurityException, IOException {
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            writeString(data, clientVersion.getBytes(StandardCharsets.US_ASCII));
            writeString(data, SERVER_VERSION.getBytes(StandardCharsets.US_ASCII));
            writeString(data, clientKexInitPayload);
            writeString(data, serverKexInitPayload);
            writeString(data, hostKeyBlob);
            writeString(data, clientPoint);
            writeString(data, serverPoint);
            data.write(encodedSecret);
            return MessageDigest.getInstance("SHA-256").digest(data.toByteArray());
        }

        private byte[] signatureBlob(byte[] exchangeHash)
                throws GeneralSecurityException, IOException {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(rsaHostKey.getPrivate());
            signer.update(exchangeHash);
            ByteArrayOutputStream blob = new ByteArrayOutputStream();
            writeString(blob, HOST_KEY_ALGORITHM.getBytes(StandardCharsets.US_ASCII));
            writeString(blob, signer.sign());
            return blob.toByteArray();
        }

        private byte[] rsaHostKeyBlob() throws IOException {
            RSAPublicKey publicKey = (RSAPublicKey) rsaHostKey.getPublic();
            ByteArrayOutputStream blob = new ByteArrayOutputStream();
            writeString(blob, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
            writeMpint(blob, publicKey.getPublicExponent());
            writeMpint(blob, publicKey.getModulus());
            return blob.toByteArray();
        }

        private void activateKeys() {
            try {
                byte[] c2sIv = deriveKey((byte) 'A', 16);
                byte[] s2cIv = deriveKey((byte) 'B', 16);
                byte[] c2sKey = deriveKey((byte) 'C', 16);
                byte[] s2cKey = deriveKey((byte) 'D', 16);
                c2sCipher = aesCtrCipher(Cipher.DECRYPT_MODE, c2sKey, c2sIv);
                s2cCipher = aesCtrCipher(Cipher.ENCRYPT_MODE, s2cKey, s2cIv);
            } catch (GeneralSecurityException ex) {
                throw new IllegalStateException(ex);
            }
        }

        private byte[] deriveKey(byte letter, int requiredLength) throws GeneralSecurityException {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(encodedSecret);
            digest.update(exchangeHash);
            digest.update(letter);
            digest.update(sessionId);
            byte[] result = digest.digest();
            while (result.length < requiredLength) {
                digest.reset();
                digest.update(encodedSecret);
                digest.update(exchangeHash);
                digest.update(result);
                byte[] next = digest.digest();
                byte[] expanded = Arrays.copyOf(result, result.length + next.length);
                System.arraycopy(next, 0, expanded, result.length, next.length);
                result = expanded;
            }
            return Arrays.copyOf(result, requiredLength);
        }

        private Cipher aesCtrCipher(int mode, byte[] key, byte[] iv)
                throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            return cipher;
        }

        private void close() {
            closed = true;
            pendingInput.offer(EOF);
        }
    }

    private record ClientPacket(byte[] payload, int totalLength) {
    }

    private static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] kexInitPayload() {
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            payload.write(20);
            payload.write(new byte[16]);
            writeNameList(payload, KEX_ALGORITHM);
            writeNameList(payload, HOST_KEY_ALGORITHM);
            writeNameList(payload, CIPHER_ALGORITHM);
            writeNameList(payload, CIPHER_ALGORITHM);
            writeNameList(payload, MAC_ALGORITHM);
            writeNameList(payload, MAC_ALGORITHM);
            writeNameList(payload, COMPRESSION_ALGORITHM);
            writeNameList(payload, COMPRESSION_ALGORITHM);
            writeNameList(payload, "");
            writeNameList(payload, "");
            payload.write(0);
            writeUint32(payload, 0);
            return payload.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] packet(byte[] payload, int blockSize) {
        int paddingLength = blockSize - ((payload.length + 5) % blockSize);
        if (paddingLength < 4) {
            paddingLength += blockSize;
        }
        int packetLength = payload.length + paddingLength + 1;
        ByteBuffer buffer = ByteBuffer.allocate(packetLength + 4);
        buffer.putInt(packetLength);
        buffer.put((byte) paddingLength);
        buffer.put(payload);
        buffer.put(new byte[paddingLength]);
        return buffer.array();
    }

    private static byte[] ecPoint(ECPublicKey publicKey) {
        byte[] x = unsignedFixedLength(publicKey.getW().getAffineX(), 32);
        byte[] y = unsignedFixedLength(publicKey.getW().getAffineY(), 32);
        ByteArrayOutputStream point = new ByteArrayOutputStream();
        point.write(4);
        point.writeBytes(x);
        point.writeBytes(y);
        return point.toByteArray();
    }

    private static byte[] normalize(byte[] secret) {
        int offset = 0;
        while (offset < secret.length - 1
                && secret[offset] == 0
                && (secret[offset + 1] & 0x80) == 0) {
            offset++;
        }
        return Arrays.copyOfRange(secret, offset, secret.length);
    }

    private static byte[] mpint(byte[] value) {
        if ((value[0] & 0x80) == 0) {
            return withLength(value);
        }
        byte[] positive = new byte[value.length + 1];
        System.arraycopy(value, 0, positive, 1, value.length);
        return withLength(positive);
    }

    private static void writeMpint(ByteArrayOutputStream out, BigInteger value) throws IOException {
        writeString(out, value.toByteArray());
    }

    private static byte[] unsignedFixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[length];
        int copyLength = Math.min(bytes.length, length);
        System.arraycopy(bytes, bytes.length - copyLength, result, length - copyLength, copyLength);
        return result;
    }

    private static ParsedString readString(byte[] bytes, int offset) {
        int length = readUint32(bytes, offset);
        int start = offset + Integer.BYTES;
        return new ParsedString(Arrays.copyOfRange(bytes, start, start + length), start + length);
    }

    private static void writeNameList(ByteArrayOutputStream out, String value) throws IOException {
        writeString(out, value.getBytes(StandardCharsets.US_ASCII));
    }

    private static void writeString(ByteArrayOutputStream out, byte[] value) throws IOException {
        writeUint32(out, value.length);
        out.write(value);
    }

    private static byte[] withLength(byte[] value) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + value.length);
        buffer.putInt(value.length);
        buffer.put(value);
        return buffer.array();
    }

    private static void writeUint32(ByteArrayOutputStream out, int value) throws IOException {
        out.write((value >>> 24) & 0xff);
        out.write((value >>> 16) & 0xff);
        out.write((value >>> 8) & 0xff);
        out.write(value & 0xff);
    }

    private static int readUint32(byte[] bytes, int offset) {
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

    private record ParsedString(byte[] value, int nextOffset) {
    }
}
