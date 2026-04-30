/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.Buffer;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.KeyExchange;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.Signature;
import com.jcraft.jsch.SignatureDSA;
import com.jcraft.jsch.SignatureECDSA;
import com.jcraft.jsch.SignatureEdDSA;
import com.jcraft.jsch.SignatureRSA;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

public class KeyExchangeTest {
    private static final byte[] EXCHANGE_HASH = {0x41, 0x42, 0x43};
    private static final byte[] RSA_EXPONENT = {0x01, 0x00, 0x01};
    private static final byte[] RSA_MODULUS = {0x21, 0x22, 0x23, 0x24};
    private static final byte[] DSA_P = {0x31, 0x32, 0x33};
    private static final byte[] DSA_Q = {0x41, 0x42};
    private static final byte[] DSA_G = {0x51, 0x52, 0x53};
    private static final byte[] DSA_F = {0x61, 0x62, 0x63};
    private static final byte[] ECDSA_R = {0x21, 0x22};
    private static final byte[] ECDSA_S = {0x31, 0x32};
    private static final byte[] EDDSA_PUBLIC_KEY = {0x51, 0x52, 0x53, 0x54};

    @Test
    void verifySshRsaLoadsConfiguredSignature() throws Exception {
        RecordingRsaSignature.reset();
        Session session = newSession();
        session.setConfig("rsa-sha2-256", RecordingRsaSignature.class.getName());

        TestableKeyExchange keyExchange = new TestableKeyExchange();
        boolean verified = keyExchange.verifyHostKey("ssh-rsa", session,
                encodedStrings(RSA_EXPONENT, RSA_MODULUS), signatureBlob("rsa-sha2-256"));

        assertThat(verified).isTrue();
        assertThat(keyExchange.getKeyType()).isEqualTo("RSA");
        assertThat(keyExchange.getKeyAlgorithName()).isEqualTo("ssh-rsa");
        assertThat(RecordingRsaSignature.initialized.get()).isTrue();
        assertThat(RecordingRsaSignature.publicKeyConfigured.get()).isTrue();
        assertThat(RecordingRsaSignature.updated.get()).isTrue();
        assertThat(RecordingRsaSignature.verified.get()).isTrue();
    }

    @Test
    void verifySshDssLoadsConfiguredSignature() throws Exception {
        RecordingDsaSignature.reset();
        Session session = newSession();
        session.setConfig("signature.dss", RecordingDsaSignature.class.getName());

        TestableKeyExchange keyExchange = new TestableKeyExchange();
        boolean verified = keyExchange.verifyHostKey("ssh-dss", session,
                encodedStrings(DSA_P, DSA_Q, DSA_G, DSA_F), signatureBlob("ssh-dss"));

        assertThat(verified).isTrue();
        assertThat(keyExchange.getKeyType()).isEqualTo("DSA");
        assertThat(keyExchange.getKeyAlgorithName()).isEqualTo("ssh-dss");
        assertThat(RecordingDsaSignature.initialized.get()).isTrue();
        assertThat(RecordingDsaSignature.publicKeyConfigured.get()).isTrue();
        assertThat(RecordingDsaSignature.updated.get()).isTrue();
        assertThat(RecordingDsaSignature.verified.get()).isTrue();
    }

    @Test
    void verifyEcdsaLoadsConfiguredSignature() throws Exception {
        RecordingEcdsaSignature.reset();
        Session session = newSession();
        session.setConfig("ecdsa-sha2-nistp256", RecordingEcdsaSignature.class.getName());

        TestableKeyExchange keyExchange = new TestableKeyExchange();
        boolean verified = keyExchange.verifyHostKey("ecdsa-sha2-nistp256", session,
                encodedStrings("nistp256".getBytes(StandardCharsets.US_ASCII), ecdsaPoint()),
                signatureBlob("ecdsa-sha2-nistp256"));

        assertThat(verified).isTrue();
        assertThat(keyExchange.getKeyType()).isEqualTo("ECDSA");
        assertThat(keyExchange.getKeyAlgorithName()).isEqualTo("ecdsa-sha2-nistp256");
        assertThat(RecordingEcdsaSignature.initialized.get()).isTrue();
        assertThat(RecordingEcdsaSignature.publicKeyConfigured.get()).isTrue();
        assertThat(RecordingEcdsaSignature.updated.get()).isTrue();
        assertThat(RecordingEcdsaSignature.verified.get()).isTrue();
    }

    @Test
    void verifyEdDsaLoadsConfiguredSignature() throws Exception {
        RecordingEdDsaSignature.reset();
        Session session = newSession();
        session.setConfig("ssh-ed25519", RecordingEdDsaSignature.class.getName());

        TestableKeyExchange keyExchange = new TestableKeyExchange();
        boolean verified = keyExchange.verifyHostKey("ssh-ed25519", session,
                encodedStrings(EDDSA_PUBLIC_KEY), signatureBlob("ssh-ed25519"));

        assertThat(verified).isTrue();
        assertThat(keyExchange.getKeyType()).isEqualTo("EDDSA");
        assertThat(keyExchange.getKeyAlgorithName()).isEqualTo("ssh-ed25519");
        assertThat(RecordingEdDsaSignature.initialized.get()).isTrue();
        assertThat(RecordingEdDsaSignature.publicKeyConfigured.get()).isTrue();
        assertThat(RecordingEdDsaSignature.updated.get()).isTrue();
        assertThat(RecordingEdDsaSignature.verified.get()).isTrue();
    }

    private static Session newSession() throws Exception {
        return new JSch().getSession("coverage", "example.test", 22);
    }

    private static byte[] signatureBlob(String algorithm) {
        byte[] algorithmBytes = algorithm.getBytes(StandardCharsets.US_ASCII);
        return encodedStrings(algorithmBytes, new byte[] {0x55, 0x66});
    }

    private static byte[] ecdsaPoint() {
        byte[] point = new byte[1 + ECDSA_R.length + ECDSA_S.length];
        point[0] = 0x04;
        System.arraycopy(ECDSA_R, 0, point, 1, ECDSA_R.length);
        System.arraycopy(ECDSA_S, 0, point, 1 + ECDSA_R.length, ECDSA_S.length);
        return point;
    }

    private static byte[] encodedStrings(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            writeInt(output, value.length);
            output.writeBytes(value);
        }
        return output.toByteArray();
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write((value >>> 24) & 0xff);
        output.write((value >>> 16) & 0xff);
        output.write((value >>> 8) & 0xff);
        output.write(value & 0xff);
    }

    public static final class TestableKeyExchange extends KeyExchange {
        public boolean verifyHostKey(String algorithm, Session configuredSession, byte[] hostKey,
                byte[] signature) throws Exception {
            session = configuredSession;
            H = EXCHANGE_HASH;
            return verify(algorithm, hostKey, 0, signature);
        }

        @Override
        public void init(Session session, byte[] serverVersion, byte[] clientVersion,
                byte[] serverKexInit, byte[] clientKexInit) {
        }

        @Override
        public boolean next(Buffer buffer) {
            return false;
        }

        @Override
        public int getState() {
            return STATE_END;
        }
    }

    public abstract static class RecordingSignature implements Signature {
        private final AtomicBoolean initialized;
        private final AtomicBoolean updated;
        private final AtomicBoolean verified;

        RecordingSignature(AtomicBoolean initialized, AtomicBoolean updated,
                AtomicBoolean verified) {
            this.initialized = initialized;
            this.updated = updated;
            this.verified = verified;
        }

        @Override
        public void init() {
            initialized.set(true);
        }

        @Override
        public void update(byte[] hash) {
            assertThat(hash).containsExactly(EXCHANGE_HASH);
            updated.set(true);
        }

        @Override
        public boolean verify(byte[] signature) {
            assertThat(signature).isNotEmpty();
            verified.set(true);
            return true;
        }

        @Override
        public byte[] sign() {
            return new byte[] {0x01};
        }
    }

    public static final class RecordingRsaSignature extends RecordingSignature
            implements SignatureRSA {
        private static final AtomicBoolean initialized = new AtomicBoolean();
        private static final AtomicBoolean publicKeyConfigured = new AtomicBoolean();
        private static final AtomicBoolean updated = new AtomicBoolean();
        private static final AtomicBoolean verified = new AtomicBoolean();

        public RecordingRsaSignature() {
            super(initialized, updated, verified);
        }

        private static void reset() {
            initialized.set(false);
            publicKeyConfigured.set(false);
            updated.set(false);
            verified.set(false);
        }

        @Override
        public void setPubKey(byte[] exponent, byte[] modulus) {
            assertThat(exponent).containsExactly(RSA_EXPONENT);
            assertThat(modulus).containsExactly(RSA_MODULUS);
            publicKeyConfigured.set(true);
        }

        @Override
        public void setPrvKey(byte[] privateExponent, byte[] modulus) {
        }
    }

    public static final class RecordingDsaSignature extends RecordingSignature
            implements SignatureDSA {
        private static final AtomicBoolean initialized = new AtomicBoolean();
        private static final AtomicBoolean publicKeyConfigured = new AtomicBoolean();
        private static final AtomicBoolean updated = new AtomicBoolean();
        private static final AtomicBoolean verified = new AtomicBoolean();

        public RecordingDsaSignature() {
            super(initialized, updated, verified);
        }

        private static void reset() {
            initialized.set(false);
            publicKeyConfigured.set(false);
            updated.set(false);
            verified.set(false);
        }

        @Override
        public void setPubKey(byte[] publicKey, byte[] p, byte[] q, byte[] g) {
            assertThat(publicKey).containsExactly(DSA_F);
            assertThat(p).containsExactly(DSA_P);
            assertThat(q).containsExactly(DSA_Q);
            assertThat(g).containsExactly(DSA_G);
            publicKeyConfigured.set(true);
        }

        @Override
        public void setPrvKey(byte[] privateKey, byte[] p, byte[] q, byte[] g) {
        }
    }

    public static final class RecordingEcdsaSignature extends RecordingSignature
            implements SignatureECDSA {
        private static final AtomicBoolean initialized = new AtomicBoolean();
        private static final AtomicBoolean publicKeyConfigured = new AtomicBoolean();
        private static final AtomicBoolean updated = new AtomicBoolean();
        private static final AtomicBoolean verified = new AtomicBoolean();

        public RecordingEcdsaSignature() {
            super(initialized, updated, verified);
        }

        private static void reset() {
            initialized.set(false);
            publicKeyConfigured.set(false);
            updated.set(false);
            verified.set(false);
        }

        @Override
        public void setPubKey(byte[] r, byte[] s) {
            assertThat(r).containsExactly(ECDSA_R);
            assertThat(s).containsExactly(ECDSA_S);
            publicKeyConfigured.set(true);
        }

        @Override
        public void setPrvKey(byte[] privateKey) {
        }
    }

    public static final class RecordingEdDsaSignature extends RecordingSignature
            implements SignatureEdDSA {
        private static final AtomicBoolean initialized = new AtomicBoolean();
        private static final AtomicBoolean publicKeyConfigured = new AtomicBoolean();
        private static final AtomicBoolean updated = new AtomicBoolean();
        private static final AtomicBoolean verified = new AtomicBoolean();

        public RecordingEdDsaSignature() {
            super(initialized, updated, verified);
        }

        private static void reset() {
            initialized.set(false);
            publicKeyConfigured.set(false);
            updated.set(false);
            verified.set(false);
        }

        @Override
        public void setPubKey(byte[] publicKey) {
            assertThat(publicKey).containsExactly(EDDSA_PUBLIC_KEY);
            publicKeyConfigured.set(true);
        }

        @Override
        public void setPrvKey(byte[] privateKey) {
        }
    }
}
