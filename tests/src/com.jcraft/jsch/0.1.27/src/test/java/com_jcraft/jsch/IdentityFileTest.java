/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_jcraft.jsch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.Hashtable;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class IdentityFileTest {
    private static final String RSA_IDENTITY = """
            -----BEGIN RSA PRIVATE KEY-----
            MIICXAIBAAKBgQCnFJXhVtyIYcGHTN14fXPWGIa9Y3D3y9Wvg78JkA+fFXFhUzyX
            EL2bWqDYWmyKA31K/E+UdJ+Od58eiNmEBIPKrk+Q31w319v4z7GytToez759fP5a
            /XXVho7sbMEREHlc+LNSN9xMRD8OSDAcPHLOKhTD6gpbfT1o1T4txJA74wIDAQAB
            AoGBAIupW3fXYdTm0hzFFcPoKfHm4ophG0cnLeHe4Mufjk9OX04Zg+ZK6hN4eEch
            nuTm8c7Chcr0iYNPXsTpY9lE5NwnGhstVniwgrDn+mhkm1uyeJm6i5Trno1xzadO
            Y4dkxTYJtdUuDd+ON9mWhll00BU/NNgDRrNJajl5atcFBkyxAkEA2SUsbwpZ7UCH
            aoSnBZ38ohT4IjzEOj88oIyoDP/1CSWxK7pmqLZgdB5pU4ve20uJl8ucGUDf/fKx
            AwJjbnoSbwJBAMT6FLM6/MXVQshs/+CHWwqCjlJKtWTBHIJTwpyyUy3DwWn6is76
            MUEdoF/QYbPXPveRxPE2/Qshi06oPasQl80CQEFVA+dsz92oGpYbzzzLaeVCNd3n
            Norn18/eQfmXURwtcP7dGGIObTrCo6H1ZVnJgl5fblnSwRHg4Q0svMnShJUCQEBD
            71kCl9Hb4GwP0/PndXaaXeDPaxsyPiDvPwFs1OKLiaEQExWwXz2FcvuPuzC3u11+
            e6jPbgOhsBe7oFMdmMkCQBOE3se4Ppni6ZDYx2Iyr7VqUSrw0jMPKSbg275mxo/X
            SM9ONTGUzH5/HH/R37I+4PciF/et4HF27p1hJ5xpVpA=
            -----END RSA PRIVATE KEY-----
            """;

    private static final String DSA_IDENTITY = """
            -----BEGIN DSA PRIVATE KEY-----
            MIIBuwIBAAKBgQCxDCKLelKMtqJUxaAniRYP+YclevYtD6vUEiiPRE89GpAIwHOC
            a2xUmOwHXRgJfQjg5oAZPJkudzp05k1+dqnRx26muwEUgMtWhmvPn1oqWd7dU6vi
            UHm5okDZgkcfvy1o3VD1agF5l9tJPLmDh8oZS7SSkv1LM6F0hwKtZJ+GLwIVAL7R
            vHci0mKcA6QEBKuvUfpjIoQ/AoGBAIJ67m1Qw3COJf2IbofLwGTEa90aonJLL1wP
            HQgZgucpptILC48iCqdtREFk6GvhMyg5/W4ksC8qlRg6RbSKt8i726E8yjMXuoCb
            HbZpzRktL9Wykd4KbcG7FpQXdNJ7WC8GXX8t8EtsePhhMrU5IwM1nEpsK+ze96if
            Wcmex+RsAoGAMWNjypY1Tnrt6vJY6pK7sejP3VxgPANRriKAN0aQvBd4wxKMlmdh
            lp8CxvzWeIHkkD/6DdFAD/gjqmmk8jC2tHjIDT4DKV3VEwDThPG+/2eZtBqE7Ctw
            zsoKAfYFWbUynZ6LkTUR2EHO77H0QzL16ANTBC2NZAztDyml2c3O2nACFC5BTe72
            EVDRJEieMEkXY/0Dzyyq
            -----END DSA PRIVATE KEY-----
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void signsPublicKeyAuthenticationRequestWithRsaIdentity() throws Exception {
        connectWithIdentity(RSA_IDENTITY, "id_rsa");
    }

    @Test
    void signsPublicKeyAuthenticationRequestWithDsaIdentity() throws Exception {
        connectWithIdentity(DSA_IDENTITY, "id_dsa");
    }

    private void connectWithIdentity(String pem, String fileName) throws Exception {
        Path identity = temporaryDirectory.resolve(fileName);
        Files.writeString(identity, pem, StandardCharsets.US_ASCII);

        try (MinimalSshServer server = MinimalSshServer.start()) {
            JSch jsch = new JSch();
            jsch.addIdentity(identity.toString());

            Session session = jsch.getSession("user", server.host(), server.port());
            Hashtable<String, String> config = new Hashtable<>();
            config.put("StrictHostKeyChecking", "no");
            config.put("kex", "diffie-hellman-group1-sha1");
            config.put("server_host_key", "ssh-rsa");
            config.put("cipher.c2s", "3des-cbc");
            config.put("cipher.s2c", "3des-cbc");
            config.put("mac.c2s", "hmac-md5");
            config.put("mac.s2c", "hmac-md5");
            session.setConfig(config);

            Throwable thrown = catchThrowable(() -> session.connect(10_000));

            assertThat(thrown).isInstanceOf(JSchException.class).hasMessageContaining("Auth fail");
            assertThat(server.sawSignedPublicKeyRequest()).isTrue();
        }
    }

    private static final class MinimalSshServer implements Closeable {
        private static final byte[] GROUP1_P = new BigInteger(
                        "00FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08"
                                + "8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD"
                                + "3A431B302B0A6DF25F14374FE1356D6D51C245E485B576625E"
                                + "7EC6F44C42E9A637ED6B0BFF5CB6F406B7EDEE386BFB5A899"
                                + "FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF",
                        16)
                .toByteArray();

        private final ServerSocket serverSocket;
        private final Thread thread;
        private volatile boolean sawSignedPublicKeyRequest;
        private volatile IOException failure;

        private MinimalSshServer(ServerSocket serverSocket) {
            this.serverSocket = serverSocket;
            this.thread = new Thread(this::serve, "minimal-jsch-server");
            this.thread.start();
        }

        static MinimalSshServer start() throws IOException {
            ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            return new MinimalSshServer(serverSocket);
        }

        String host() {
            return serverSocket.getInetAddress().getHostAddress();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        boolean sawSignedPublicKeyRequest() throws IOException, InterruptedException {
            thread.join(10_000);
            if (failure != null) {
                throw failure;
            }
            return sawSignedPublicKeyRequest;
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                SshTransport transport = new SshTransport(socket.getInputStream(), socket.getOutputStream());
                KeyPair hostKeyPair = generateHostKeyPair();
                byte[] serverVersion = "SSH-2.0-identity-file-test".getBytes(StandardCharsets.US_ASCII);
                socket.getOutputStream().write(serverVersion);
                socket.getOutputStream().write('\r');
                socket.getOutputStream().write('\n');
                socket.getOutputStream().flush();

                byte[] clientVersion = readVersion(socket.getInputStream());

                byte[] serverKexInit = kexInitPayload();
                transport.writePlainPacket(serverKexInit);
                byte[] clientKexInit = transport.readPlainPacket();
                byte[] clientDhInit = transport.readPlainPacket();

                KexResult kex = buildKex(hostKeyPair, clientVersion, serverVersion, clientKexInit, serverKexInit,
                        clientDhInit);
                transport.writePlainPacket(kex.replyPayload);
                transport.readPlainPacket();
                transport.writePlainPacket(new byte[] {21});
                transport.installKeys(kex.sharedSecret, kex.exchangeHash, kex.exchangeHash);

                byte[] serviceRequest = transport.readEncryptedPacket();
                assertThat(serviceRequest[0]).isEqualTo((byte) 5);
                transport.writeEncryptedPacket(serviceAcceptPayload());

                byte[] noneAuth = transport.readEncryptedPacket();
                assertThat(noneAuth[0]).isEqualTo((byte) 50);
                transport.writeEncryptedPacket(userAuthFailurePayload());

                PublicKeyRequest publicKeyRequest = parsePublicKeyRequest(transport.readEncryptedPacket());
                if (!publicKeyRequest.signed) {
                    byte[] publicKeyAccepted = publicKeyOkPayload(publicKeyRequest.algorithm, publicKeyRequest.blob);
                    transport.writeEncryptedPacket(publicKeyAccepted);
                    publicKeyRequest = parsePublicKeyRequest(transport.readEncryptedPacket());
                }
                sawSignedPublicKeyRequest = publicKeyRequest.signed;
                transport.writeEncryptedPacket(userAuthFailurePayload());
            } catch (EOFException expected) {
                // The client closes the connection after the expected authentication failure.
            } catch (IOException exception) {
                failure = exception;
            } catch (Exception exception) {
                failure = new IOException(exception);
            }
        }

        private static KeyPair generateHostKeyPair() throws GeneralSecurityException {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return generator.generateKeyPair();
        }

        private static byte[] readVersion(InputStream inputStream) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int value = inputStream.read();
                if (value == -1) {
                    throw new EOFException("missing SSH version line");
                }
                if (value == '\n') {
                    byte[] version = line.toByteArray();
                    if (version.length > 0 && version[version.length - 1] == '\r') {
                        return Arrays.copyOf(version, version.length - 1);
                    }
                    return version;
                }
                line.write(value);
            }
        }

        private static byte[] kexInitPayload() throws IOException {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            payload.write(20);
            payload.write(new byte[16]);
            writeString(payload, "diffie-hellman-group1-sha1".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "3des-cbc".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "3des-cbc".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "hmac-md5".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "hmac-md5".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "none".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, "none".getBytes(StandardCharsets.US_ASCII));
            writeString(payload, new byte[0]);
            writeString(payload, new byte[0]);
            payload.write(0);
            writeInt(payload, 0);
            return payload.toByteArray();
        }

        private static KexResult buildKex(KeyPair hostKeyPair, byte[] clientVersion, byte[] serverVersion,
                byte[] clientKexInit, byte[] serverKexInit, byte[] clientDhInit) throws Exception {
            PacketReader reader = new PacketReader(clientDhInit);
            assertThat(reader.readByte()).isEqualTo(30);
            byte[] eBytes = reader.readString();
            BigInteger e = new BigInteger(eBytes);
            BigInteger p = new BigInteger(GROUP1_P);
            BigInteger g = BigInteger.valueOf(2L);
            BigInteger y = new BigInteger("123456789abcdef123456789abcdef", 16);
            BigInteger f = g.modPow(y, p);
            BigInteger sharedSecret = e.modPow(y, p);

            byte[] hostKeyBlob = hostKeyBlob(hostKeyPair);
            byte[] fBytes = f.toByteArray();
            byte[] sharedSecretBytes = fixedLengthUnsigned(sharedSecret, 128);
            ByteArrayOutputStream exchange = new ByteArrayOutputStream();
            writeString(exchange, clientVersion);
            writeString(exchange, serverVersion);
            writeString(exchange, clientKexInit);
            writeString(exchange, serverKexInit);
            writeString(exchange, hostKeyBlob);
            writeMpInt(exchange, eBytes);
            writeMpInt(exchange, fBytes);
            writeMpInt(exchange, sharedSecretBytes);
            byte[] exchangeHash = MessageDigest.getInstance("SHA-1").digest(exchange.toByteArray());

            byte[] signature = signExchangeHash(hostKeyPair.getPrivate(), exchangeHash);
            ByteArrayOutputStream reply = new ByteArrayOutputStream();
            reply.write(31);
            writeString(reply, hostKeyBlob);
            writeString(reply, fBytes);
            writeString(reply, signature);
            return new KexResult(reply.toByteArray(), sharedSecretBytes, exchangeHash);
        }

        private static byte[] hostKeyBlob(KeyPair hostKeyPair) throws IOException {
            RSAPublicKey publicKey = (RSAPublicKey) hostKeyPair.getPublic();
            ByteArrayOutputStream blob = new ByteArrayOutputStream();
            writeString(blob, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
            writeString(blob, publicKey.getPublicExponent().toByteArray());
            writeString(blob, publicKey.getModulus().toByteArray());
            return blob.toByteArray();
        }

        private static byte[] fixedLengthUnsigned(BigInteger value, int length) {
            byte[] bytes = value.toByteArray();
            if (bytes.length == length) {
                return bytes;
            }
            if (bytes.length == length + 1 && bytes[0] == 0) {
                return Arrays.copyOfRange(bytes, 1, bytes.length);
            }
            byte[] fixed = new byte[length];
            System.arraycopy(bytes, 0, fixed, length - bytes.length, bytes.length);
            return fixed;
        }

        private static byte[] signExchangeHash(PrivateKey privateKey, byte[] exchangeHash) throws Exception {
            Signature signer = Signature.getInstance("SHA1withRSA");
            signer.initSign(privateKey);
            signer.update(exchangeHash);
            ByteArrayOutputStream blob = new ByteArrayOutputStream();
            writeString(blob, "ssh-rsa".getBytes(StandardCharsets.US_ASCII));
            writeString(blob, signer.sign());
            return blob.toByteArray();
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

        private static byte[] publicKeyOkPayload(String algorithm, byte[] blob) throws IOException {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            payload.write(60);
            writeString(payload, algorithm.getBytes(StandardCharsets.US_ASCII));
            writeString(payload, blob);
            return payload.toByteArray();
        }

        private static PublicKeyRequest parsePublicKeyRequest(byte[] payload) throws IOException {
            PacketReader reader = new PacketReader(payload);
            assertThat(reader.readByte()).isEqualTo(50);
            reader.readString();
            reader.readString();
            assertThat(new String(reader.readString(), StandardCharsets.US_ASCII)).isEqualTo("publickey");
            boolean signed = reader.readByte() != 0;
            String algorithm = new String(reader.readString(), StandardCharsets.US_ASCII);
            byte[] blob = reader.readString();
            return new PublicKeyRequest(signed, algorithm, blob);
        }
    }

    private static final class SshTransport {
        private final DataInputStream input;
        private final OutputStream output;
        private Cipher serverToClientCipher;
        private Cipher clientToServerCipher;
        private Mac serverToClientMac;
        private Mac clientToServerMac;
        private int incomingSequence;
        private int outgoingSequence;

        SshTransport(InputStream input, OutputStream output) {
            this.input = new DataInputStream(input);
            this.output = output;
        }

        byte[] readPlainPacket() throws IOException {
            return readPacket(null, null, false);
        }

        byte[] readEncryptedPacket() throws IOException {
            return readPacket(clientToServerCipher, clientToServerMac, true);
        }

        void writePlainPacket(byte[] payload) throws IOException {
            writePacket(payload, null, null, false);
        }

        void writeEncryptedPacket(byte[] payload) throws IOException {
            writePacket(payload, serverToClientCipher, serverToClientMac, true);
        }

        void installKeys(byte[] sharedSecret, byte[] exchangeHash, byte[] sessionId) throws Exception {
            byte[] ivClientToServer = deriveKey(sharedSecret, exchangeHash, (byte) 'A', sessionId, 8);
            byte[] ivServerToClient = deriveKey(sharedSecret, exchangeHash, (byte) 'B', sessionId, 8);
            byte[] keyClientToServer = deriveKey(sharedSecret, exchangeHash, (byte) 'C', sessionId, 24);
            byte[] keyServerToClient = deriveKey(sharedSecret, exchangeHash, (byte) 'D', sessionId, 24);
            byte[] macClientToServer = deriveKey(sharedSecret, exchangeHash, (byte) 'E', sessionId, 16);
            byte[] macServerToClient = deriveKey(sharedSecret, exchangeHash, (byte) 'F', sessionId, 16);
            clientToServerCipher = tripleDes(Cipher.DECRYPT_MODE, keyClientToServer, ivClientToServer);
            serverToClientCipher = tripleDes(Cipher.ENCRYPT_MODE, keyServerToClient, ivServerToClient);
            clientToServerMac = hmacMd5(macClientToServer);
            serverToClientMac = hmacMd5(macServerToClient);
        }

        private byte[] readPacket(Cipher cipher, Mac mac, boolean authenticated) throws IOException {
            byte[] firstBlock = input.readNBytes(8);
            if (firstBlock.length != 8) {
                throw new EOFException("packet header missing");
            }
            byte[] plainFirstBlock = firstBlock.clone();
            if (cipher != null) {
                plainFirstBlock = cipher.update(firstBlock);
            }
            int packetLength = readInt(plainFirstBlock, 0);
            byte[] rest = input.readNBytes(packetLength - 4);
            if (rest.length != packetLength - 4) {
                throw new EOFException("packet body missing");
            }
            byte[] plainRest = rest;
            if (cipher != null) {
                plainRest = cipher.update(rest);
            }
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            packet.write(plainFirstBlock);
            packet.write(plainRest);
            byte[] packetBytes = packet.toByteArray();
            if (authenticated) {
                input.readNBytes(mac.getMacLength());
            }
            incomingSequence++;
            int paddingLength = packetBytes[4] & 0xff;
            return Arrays.copyOfRange(packetBytes, 5, 4 + packetLength - paddingLength);
        }

        private void writePacket(byte[] payload, Cipher cipher, Mac mac, boolean authenticated) throws IOException {
            int paddingLength = 8 - ((payload.length + 5) % 8);
            if (paddingLength < 4) {
                paddingLength += 8;
            }
            int packetLength = payload.length + paddingLength + 1;
            ByteArrayOutputStream packet = new ByteArrayOutputStream();
            writeInt(packet, packetLength);
            packet.write(paddingLength);
            packet.write(payload);
            packet.write(new byte[paddingLength]);
            byte[] packetBytes = packet.toByteArray();
            byte[] macBytes = new byte[0];
            if (authenticated) {
                mac.update(sequenceBytes(outgoingSequence));
                mac.update(packetBytes);
                macBytes = mac.doFinal();
            }
            if (cipher != null) {
                packetBytes = cipher.update(packetBytes);
            }
            output.write(packetBytes);
            output.write(macBytes);
            output.flush();
            outgoingSequence++;
        }

        private static Cipher tripleDes(int mode, byte[] key, byte[] iv) throws GeneralSecurityException {
            Cipher cipher = Cipher.getInstance("DESede/CBC/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "DESede"), new IvParameterSpec(iv));
            return cipher;
        }

        private static Mac hmacMd5(byte[] key) throws GeneralSecurityException {
            Mac mac = Mac.getInstance("HmacMD5");
            mac.init(new SecretKeySpec(key, "HmacMD5"));
            return mac;
        }

        private static byte[] deriveKey(
                byte[] sharedSecret, byte[] exchangeHash, byte letter, byte[] sessionId, int size) throws Exception {
            ByteArrayOutputStream seed = new ByteArrayOutputStream();
            writeMpInt(seed, sharedSecret);
            seed.write(exchangeHash);
            seed.write(letter);
            seed.write(sessionId);
            byte[] key = MessageDigest.getInstance("SHA-1").digest(seed.toByteArray());
            while (key.length < size) {
                ByteArrayOutputStream expansionSeed = new ByteArrayOutputStream();
                writeMpInt(expansionSeed, sharedSecret);
                expansionSeed.write(exchangeHash);
                expansionSeed.write(key);
                byte[] next = MessageDigest.getInstance("SHA-1").digest(expansionSeed.toByteArray());
                byte[] expanded = Arrays.copyOf(key, key.length + next.length);
                System.arraycopy(next, 0, expanded, key.length, next.length);
                key = expanded;
            }
            return Arrays.copyOf(key, size);
        }
    }

    private static final class PacketReader {
        private final byte[] data;
        private int index;

        PacketReader(byte[] data) {
            this.data = data;
        }

        int readByte() {
            return data[index++] & 0xff;
        }

        byte[] readString() throws IOException {
            int length = readInt(data, index);
            index += 4;
            byte[] value = Arrays.copyOfRange(data, index, index + length);
            index += length;
            return value;
        }
    }

    private record KexResult(byte[] replyPayload, byte[] sharedSecret, byte[] exchangeHash) {}

    private record PublicKeyRequest(boolean signed, String algorithm, byte[] blob) {}

    private static void writeString(ByteArrayOutputStream output, byte[] value) throws IOException {
        writeInt(output, value.length);
        output.write(value);
    }

    private static void writeMpInt(ByteArrayOutputStream output, byte[] value) throws IOException {
        if ((value[0] & 0x80) != 0) {
            writeInt(output, value.length + 1);
            output.write(0);
        } else {
            writeInt(output, value.length);
        }
        output.write(value);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }

    private static int readInt(byte[] data, int index) {
        return ((data[index] & 0xff) << 24)
                | ((data[index + 1] & 0xff) << 16)
                | ((data[index + 2] & 0xff) << 8)
                | (data[index + 3] & 0xff);
    }

    private static byte[] sequenceBytes(int sequence) {
        return new byte[] {
            (byte) (sequence >>> 24), (byte) (sequence >>> 16), (byte) (sequence >>> 8), (byte) sequence
        };
    }
}
