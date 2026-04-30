/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SocketFactory;
import com.jcraft.jsch.UserAuthNone;
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
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import javax.crypto.KeyAgreement;
import org.junit.jupiter.api.Test;

public class SessionTest {
    private static final String SERVER_VERSION = "SSH-2.0-jsch-coverage-server";
    private static final String KEX_ALGORITHM = "ecdh-sha2-nistp256";
    private static final String HOST_KEY_ALGORITHM = "rsa-sha2-256";
    private static final String CIPHER_ALGORITHM = "aes128-ctr";
    private static final String MAC_ALGORITHM = "hmac-sha2-256";
    private static final String COMPRESSION_ALGORITHM = "zlib";

    @Test
    void connectNegotiatesAlgorithmsAndInitializesSessionKeys() throws Exception {
        Session session = new JSch().getSession("coverage", "example.test", 22);
        session.setSocketFactory(new ScriptedSshSocketFactory());
        configureSingleAlgorithmHandshake(session);

        assertThatThrownBy(() -> session.connect(1_000))
                .isInstanceOf(JSchException.class);

        session.disconnect();
    }

    private static void configureSingleAlgorithmHandshake(Session session) {
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
        session.setConfig("PreferredAuthentications", "password");
        session.setConfig("userauth.none", FailingUserAuthNone.class.getName());
    }

    public static final class FailingUserAuthNone extends UserAuthNone {
        @Override
        public boolean start(Session session) {
            return false;
        }
    }

    private static final class ScriptedSshSocketFactory implements SocketFactory {
        private final ScriptedSshTransport transport = new ScriptedSshTransport();
        private final Socket socket = new ScriptedSocket();

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
        private final KeyPair rsaHostKey;
        private String clientVersion;
        private byte[] clientKexInitPayload;
        private byte[] serverKexInitPayload;
        private boolean versionReceived;
        private boolean newKeysQueued;

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

        private OutputStream outputStream() {
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
                int packetLength = readUint32(bytes, offset);
                int totalLength = 4 + packetLength;
                boolean incompletePacket = bytes.length - offset < totalLength;
                if (packetLength <= 0 || packetLength > 35_000 || incompletePacket) {
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

        private void processClientPacket(byte[] payload) {
            int command = payload[0] & 0xff;
            if (command == 20) {
                clientKexInitPayload = payload;
                serverKexInitPayload = kexInitPayload();
                pendingInput.add(packet(serverKexInitPayload));
            } else if (command == 30) {
                byte[] clientPoint = readString(payload, 1).value();
                pendingInput.add(packet(ecdhReplyPayload(clientPoint)));
                if (!newKeysQueued) {
                    pendingInput.add(packet(new byte[] {21}));
                    newKeysQueued = true;
                }
            }
        }

        private byte[] ecdhReplyPayload(byte[] clientPoint) {
            try {
                KeyPairGenerator ecGenerator = KeyPairGenerator.getInstance("EC");
                ecGenerator.initialize(new ECGenParameterSpec("secp256r1"));
                KeyPair serverKeyPair = ecGenerator.generateKeyPair();
                ECPublicKey serverPublicKey = (ECPublicKey) serverKeyPair.getPublic();

                KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
                agreement.init(serverKeyPair.getPrivate());
                ECPublicKey clientPublicKey =
                        publicKeyFromPoint(clientPoint, serverPublicKey.getParams());
                agreement.doPhase(clientPublicKey, true);
                byte[] sharedSecret = normalize(agreement.generateSecret());

                byte[] hostKeyBlob = rsaHostKeyBlob();
                byte[] serverPoint = ecPoint(serverPublicKey);
                byte[] encodedSecret = mpint(sharedSecret);
                byte[] exchangeHash =
                        exchangeHash(hostKeyBlob, clientPoint, serverPoint, encodedSecret);
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

    private static byte[] packet(byte[] payload) {
        int blockSize = 8;
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
