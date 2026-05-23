/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.jcraft.jsch.DHG1;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.jce.DH;
import com.jcraft.jsch.jce.HMACSHA1;
import com.jcraft.jsch.jce.MD5;
import com.jcraft.jsch.jce.Random;
import com.jcraft.jsch.jce.SHA1;
import com.jcraft.jsch.jce.SignatureRSA;
import com.jcraft.jsch.jce.TripleDESCBC;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

public class SessionTest {
    private static final String USER = "session-coverage";
    private static final BigInteger GROUP1_P = new BigInteger("""
            FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08
            8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B
            302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9
            A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6
            49286651ECE65381FFFFFFFFFFFFFFFF
            """.replaceAll("\\s", ""), 16);
    private static final BigInteger GROUP1_G = BigInteger.valueOf(2L);

    @Test
    void connectsThroughKeyExchangeAndNoneAuthentication() throws Exception {
        keepSessionImplementationsReachable();
        try (MinimalSshServer server = new MinimalSshServer()) {
            JSch jsch = new JSch();
            Session session = jsch.getSession(USER, "127.0.0.1", server.port());
            Properties sessionConfig = new Properties();
            sessionConfig.setProperty("StrictHostKeyChecking", "no");
            session.setConfig(sessionConfig);

            assertThatCode(() -> session.connect(10_000)).doesNotThrowAnyException();
            session.disconnect();
        }
    }

    private static void keepSessionImplementationsReachable() {
        assertThat(new Random()).isNotNull();
        assertThat(new DHG1()).isNotNull();
        assertThat(new DH()).isNotNull();
        assertThat(new SHA1()).isNotNull();
        assertThat(new MD5()).isNotNull();
        assertThat(new SignatureRSA()).isNotNull();
        assertThat(new TripleDESCBC()).isNotNull();
        assertThat(new HMACSHA1()).isNotNull();
    }

    private static final class MinimalSshServer implements AutoCloseable {
        private static final byte SSH_MSG_KEXINIT = 20;
        private static final byte SSH_MSG_NEWKEYS = 21;
        private static final byte SSH_MSG_KEXDH_INIT = 30;
        private static final byte SSH_MSG_KEXDH_REPLY = 31;
        private static final byte SSH_MSG_SERVICE_REQUEST = 5;
        private static final byte SSH_MSG_SERVICE_ACCEPT = 6;
        private static final byte SSH_MSG_USERAUTH_REQUEST = 50;
        private static final byte SSH_MSG_USERAUTH_SUCCESS = 52;

        private final ServerSocket serverSocket;
        private final ExecutorService executorService;
        private final Future<?> serverTask;

        private Socket socket;
        private DataInputStream inputStream;
        private OutputStream outputStream;
        private Cipher decryptCipher;
        private Cipher encryptCipher;
        private byte[] clientMacKey;
        private byte[] serverMacKey;
        private int inboundSequence;
        private int outboundSequence;

        MinimalSshServer() throws IOException {
            serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            executorService = Executors.newSingleThreadExecutor();
            serverTask = executorService.submit(this::run);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            closeQuietly(socket);
            closeQuietly(serverSocket);
            executorService.shutdownNow();
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                throw new AssertionError("SSH test server did not stop");
            }
            serverTask.get(5, TimeUnit.SECONDS);
        }

        private void run() {
            try {
                socket = serverSocket.accept();
                socket.setSoTimeout(10_000);
                inputStream = new DataInputStream(socket.getInputStream());
                outputStream = socket.getOutputStream();
                exchangeKeys();
                authenticateNone();
            } catch (IOException exception) {
                if (!serverSocket.isClosed()) {
                    throw new AssertionError(exception);
                }
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        }

        private void exchangeKeys() throws Exception {
            byte[] serverVersion = "SSH-2.0-session-coverage".getBytes(StandardCharsets.UTF_8);
            outputStream.write(concat(serverVersion, new byte[] {'\r', '\n'}));
            outputStream.flush();
            byte[] clientVersion = readLine();

            byte[] serverKexInit = kexInit();
            sendPlain(serverKexInit);
            byte[] clientKexInit = receivePlain(SSH_MSG_KEXINIT);
            byte[] kexDhInit = receivePlain(SSH_MSG_KEXDH_INIT);
            byte[] clientPublicBytes = new PacketReader(kexDhInit, 1).readMpIntBytes();
            BigInteger clientPublic = new BigInteger(clientPublicBytes);

            java.security.KeyPair hostKey = rsaKeyPair();
            RSAPublicKey hostPublicKey = (RSAPublicKey) hostKey.getPublic();
            BigInteger privateExponent = new BigInteger(1024, new SecureRandom())
                    .mod(GROUP1_P.subtract(BigInteger.ONE));
            BigInteger serverPublic = GROUP1_G.modPow(privateExponent, GROUP1_P);
            byte[] serverPublicBytes = serverPublic.toByteArray();
            byte[] sharedSecret = unsignedFixedLength(clientPublic.modPow(privateExponent, GROUP1_P), 128);
            byte[] hostKeyBlob = hostKeyBlob(hostPublicKey);
            byte[] exchangeHash = exchangeHash(
                    clientVersion, serverVersion, clientKexInit, serverKexInit, hostKeyBlob, clientPublicBytes,
                    serverPublicBytes, sharedSecret);
            byte[] signature = signatureBlob(hostKey.getPrivate(), exchangeHash);

            PacketBuilder reply = new PacketBuilder();
            reply.putByte(SSH_MSG_KEXDH_REPLY);
            reply.putString(hostKeyBlob);
            reply.putMpIntBytes(serverPublicBytes);
            reply.putString(signature);
            sendPlain(reply.toByteArray());

            receivePlain(SSH_MSG_NEWKEYS);
            sendPlain(new byte[] {SSH_MSG_NEWKEYS});
            initializeCiphers(sharedSecret, exchangeHash);
        }

        private void authenticateNone() throws Exception {
            PacketReader serviceRequest = new PacketReader(receiveEncrypted(SSH_MSG_SERVICE_REQUEST), 1);
            byte[] serviceName = serviceRequest.readString();
            PacketBuilder serviceAccept = new PacketBuilder();
            serviceAccept.putByte(SSH_MSG_SERVICE_ACCEPT);
            serviceAccept.putString(serviceName);
            sendEncrypted(serviceAccept.toByteArray());

            PacketReader userAuthRequest = new PacketReader(receiveEncrypted(SSH_MSG_USERAUTH_REQUEST), 1);
            byte[] userName = userAuthRequest.readString();
            byte[] service = userAuthRequest.readString();
            byte[] method = userAuthRequest.readString();
            if (!Arrays.equals(USER.getBytes(StandardCharsets.UTF_8), userName)
                    || !Arrays.equals("ssh-connection".getBytes(StandardCharsets.UTF_8), service)
                    || !Arrays.equals("none".getBytes(StandardCharsets.UTF_8), method)) {
                throw new IOException("Expected a none user-authentication request");
            }
            sendEncrypted(new byte[] {SSH_MSG_USERAUTH_SUCCESS});
        }

        private byte[] kexInit() {
            PacketBuilder builder = new PacketBuilder();
            builder.putByte(SSH_MSG_KEXINIT);
            builder.putBytes(new byte[16]);
            builder.putString("diffie-hellman-group1-sha1".getBytes(StandardCharsets.UTF_8));
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

        private void initializeCiphers(byte[] sharedSecret, byte[] exchangeHash) throws Exception {
            byte[] ivClientToServer = deriveKey(sharedSecret, exchangeHash, (byte) 'A', 8);
            byte[] ivServerToClient = deriveKey(sharedSecret, exchangeHash, (byte) 'B', 8);
            byte[] keyClientToServer = deriveKey(sharedSecret, exchangeHash, (byte) 'C', 24);
            byte[] keyServerToClient = deriveKey(sharedSecret, exchangeHash, (byte) 'D', 24);
            clientMacKey = deriveKey(sharedSecret, exchangeHash, (byte) 'E', 20);
            serverMacKey = deriveKey(sharedSecret, exchangeHash, (byte) 'F', 20);
            decryptCipher = tripleDes(Cipher.DECRYPT_MODE, keyClientToServer, ivClientToServer);
            encryptCipher = tripleDes(Cipher.ENCRYPT_MODE, keyServerToClient, ivServerToClient);
        }

        private byte[] receivePlain(byte expectedMessage) throws IOException {
            byte[] packet = readPacket();
            inboundSequence++;
            if (packet[0] != expectedMessage) {
                throw new IOException("Expected SSH message " + expectedMessage + " but received " + packet[0]);
            }
            return packet;
        }

        private byte[] receiveEncrypted(byte expectedMessage) throws Exception {
            byte[] firstBlock = inputStream.readNBytes(8);
            if (firstBlock.length != 8) {
                throw new IOException("Unexpected end of SSH packet");
            }
            byte[] plainFirstBlock = decryptCipher.update(firstBlock);
            int packetLength = readInt(plainFirstBlock, 0);
            byte[] encryptedRemainder = inputStream.readNBytes(packetLength + 4 - 8);
            byte[] mac = inputStream.readNBytes(20);
            byte[] packet = concat(plainFirstBlock, decryptCipher.update(encryptedRemainder));
            verifyMac(clientMacKey, inboundSequence, packet, mac);
            inboundSequence++;
            byte[] payload = payload(packet);
            if (payload[0] != expectedMessage) {
                throw new IOException("Expected SSH message " + expectedMessage + " but received " + payload[0]);
            }
            return payload;
        }

        private void sendPlain(byte[] payload) throws IOException {
            outputStream.write(wrapPacket(payload));
            outputStream.flush();
            outboundSequence++;
        }

        private void sendEncrypted(byte[] payload) throws Exception {
            byte[] packet = wrapPacket(payload);
            byte[] mac = mac(serverMacKey, outboundSequence, packet);
            outputStream.write(encryptCipher.update(packet));
            outputStream.write(mac);
            outputStream.flush();
            outboundSequence++;
        }

        private byte[] readPacket() throws IOException {
            byte[] firstBlock = inputStream.readNBytes(8);
            if (firstBlock.length != 8) {
                throw new IOException("Unexpected end of SSH packet");
            }
            int packetLength = readInt(firstBlock, 0);
            byte[] remainder = inputStream.readNBytes(packetLength + 4 - 8);
            return payload(concat(firstBlock, remainder));
        }

        private byte[] readLine() throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            int value;
            while ((value = inputStream.read()) != -1) {
                if (value == '\n') {
                    break;
                }
                if (value != '\r') {
                    line.write(value);
                }
            }
            return line.toByteArray();
        }

        private static void verifyMac(byte[] key, int sequence, byte[] packet, byte[] expected) throws Exception {
            byte[] actual = mac(key, sequence, packet);
            if (!Arrays.equals(actual, expected)) {
                throw new IOException("SSH packet MAC did not match");
            }
        }

        private static byte[] mac(byte[] key, int sequence, byte[] packet) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            PacketBuilder data = new PacketBuilder();
            data.putInt(sequence);
            data.putBytes(packet);
            return mac.doFinal(data.toByteArray());
        }

        private static Cipher tripleDes(int mode, byte[] key, byte[] iv) throws Exception {
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "DESede"), new IvParameterSpec(iv));
            return cipher;
        }

        private static byte[] deriveKey(byte[] sharedSecret, byte[] exchangeHash, byte letter, int length)
                throws Exception {
            PacketBuilder builder = new PacketBuilder();
            builder.putMpIntBytes(sharedSecret);
            builder.putBytes(exchangeHash);
            builder.putByte(letter);
            builder.putBytes(exchangeHash);
            byte[] key = sha1(builder.toByteArray());
            while (key.length < length) {
                PacketBuilder extension = new PacketBuilder();
                extension.putMpIntBytes(sharedSecret);
                extension.putBytes(exchangeHash);
                extension.putBytes(key);
                key = concat(key, sha1(extension.toByteArray()));
            }
            return Arrays.copyOf(key, length);
        }

        private static byte[] exchangeHash(
                byte[] clientVersion,
                byte[] serverVersion,
                byte[] clientKexInit,
                byte[] serverKexInit,
                byte[] hostKey,
                byte[] clientPublic,
                byte[] serverPublic,
                byte[] sharedSecret) throws Exception {
            PacketBuilder builder = new PacketBuilder();
            builder.putString(clientVersion);
            builder.putString(serverVersion);
            builder.putString(clientKexInit);
            builder.putString(serverKexInit);
            builder.putString(hostKey);
            builder.putMpIntBytes(clientPublic);
            builder.putMpIntBytes(serverPublic);
            builder.putMpIntBytes(sharedSecret);
            return sha1(builder.toByteArray());
        }

        private static byte[] signatureBlob(PrivateKey privateKey, byte[] data) throws Exception {
            Signature signer = Signature.getInstance("SHA1withRSA");
            signer.initSign(privateKey);
            signer.update(data);
            PacketBuilder builder = new PacketBuilder();
            builder.putString("ssh-rsa".getBytes(StandardCharsets.UTF_8));
            builder.putString(signer.sign());
            return builder.toByteArray();
        }

        private static byte[] hostKeyBlob(RSAPublicKey key) {
            PacketBuilder builder = new PacketBuilder();
            builder.putString("ssh-rsa".getBytes(StandardCharsets.UTF_8));
            builder.putMpInt(key.getPublicExponent());
            builder.putMpInt(key.getModulus());
            return builder.toByteArray();
        }

        private static java.security.KeyPair rsaKeyPair() throws Exception {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(1024);
            return keyPairGenerator.generateKeyPair();
        }

        private static byte[] unsignedFixedLength(BigInteger value, int length) {
            byte[] bytes = value.toByteArray();
            if (bytes.length == length) {
                return bytes;
            }
            byte[] result = new byte[length];
            int sourcePosition = bytes.length == length + 1 && bytes[0] == 0 ? 1 : 0;
            int copyLength = Math.min(bytes.length - sourcePosition, length);
            System.arraycopy(bytes, bytes.length - copyLength, result, length - copyLength, copyLength);
            return result;
        }

        private static byte[] sha1(byte[] data) throws Exception {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return digest.digest(data);
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

        private static byte[] payload(byte[] packet) {
            int packetLength = readInt(packet, 0);
            int paddingLength = packet[4] & 0xff;
            return Arrays.copyOfRange(packet, 5, 4 + packetLength - paddingLength);
        }

        private static int readInt(byte[] bytes, int offset) {
            return ((bytes[offset] & 0xff) << 24)
                    | ((bytes[offset + 1] & 0xff) << 16)
                    | ((bytes[offset + 2] & 0xff) << 8)
                    | (bytes[offset + 3] & 0xff);
        }

        private static byte[] concat(byte[] first, byte[] second) {
            byte[] result = Arrays.copyOf(first, first.length + second.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }

        private static void closeQuietly(Closeable closeable) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (IOException ignored) {
                    // Best-effort cleanup after the bounded integration exchange.
                }
            }
        }
    }

    private static final class PacketReader {
        private final byte[] data;
        private int offset;

        PacketReader(byte[] data, int offset) {
            this.data = data;
            this.offset = offset;
        }

        byte readByte() {
            return data[offset++];
        }

        byte[] readString() {
            int length = readInt();
            byte[] value = Arrays.copyOfRange(data, offset, offset + length);
            offset += length;
            return value;
        }

        byte[] readMpIntBytes() {
            return readString();
        }

        private int readInt() {
            int value = ((data[offset] & 0xff) << 24)
                    | ((data[offset + 1] & 0xff) << 16)
                    | ((data[offset + 2] & 0xff) << 8)
                    | (data[offset + 3] & 0xff);
            offset += 4;
            return value;
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
            putMpIntBytes(value.toByteArray());
        }

        void putMpIntBytes(byte[] value) {
            if ((value[0] & 0x80) != 0) {
                putInt(value.length + 1);
                putByte((byte) 0);
                putBytes(value);
            } else {
                putString(value);
            }
        }

        byte[] toByteArray() {
            return outputStream.toByteArray();
        }
    }
}
