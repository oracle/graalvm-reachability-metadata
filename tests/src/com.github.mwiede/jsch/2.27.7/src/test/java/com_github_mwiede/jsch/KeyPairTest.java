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
import com.jcraft.jsch.KeyPair;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class KeyPairTest {
    private static final byte[] PASSPHRASE =
            "correct horse battery staple".getBytes(StandardCharsets.UTF_8);
    private static JSch jsch;
    private static KeyPair keyPair;

    @BeforeAll
    static void createKeyPair() throws JSchException {
        jsch = new JSch();
        keyPair = KeyPair.genKeyPair(jsch, KeyPair.RSA, 512);
    }

    @Test
    void calculatesFingerprintWithConfiguredHash() {
        assertThat(keyPair.getFingerPrint()).isNotBlank();
    }

    @Test
    void writesLegacyEncryptedPrivateKeyWithConfiguredCipherHashAndRandom() {
        byte[] privateKey = writeLegacyPrivateKey(PASSPHRASE);

        assertThat(new String(privateKey, StandardCharsets.US_ASCII))
                .contains("BEGIN RSA PRIVATE KEY")
                .contains("Proc-Type: 4,ENCRYPTED")
                .contains("DEK-Info: DES-EDE3-CBC");
    }

    @Test
    void loadsLegacyEncryptedPrivateKeysForSupportedAesCiphers() throws JSchException {
        assertThat(loadLegacyPrivateKeyWithDekInfo("AES-256-CBC", 32).isEncrypted()).isTrue();
        assertThat(loadLegacyPrivateKeyWithDekInfo("AES-192-CBC", 32).isEncrypted()).isTrue();
        assertThat(loadLegacyPrivateKeyWithDekInfo("AES-128-CBC", 32).isEncrypted()).isTrue();
    }

    @Test
    void writesAndLoadsEncryptedOpenSshV1PrivateKey() throws JSchException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyPair.writeOpenSSHv1PrivateKey(out, PASSPHRASE, "aes256-ctr", 4);

        KeyPair loaded = KeyPair.load(jsch, out.toByteArray(), null);

        assertThat(loaded.isEncrypted()).isTrue();
        assertThat(loaded.decrypt(PASSPHRASE)).isTrue();
        assertThat(loaded.getFingerPrint()).isEqualTo(keyPair.getFingerPrint());
    }

    @Test
    void loadsEncryptedPuttyVersionTwoKeyWithSha1Mac() throws JSchException {
        KeyPair loaded =
                KeyPair.load(jsch, puttyPrivateKey("PuTTY-User-Key-File-2", "ssh-rsa"), null);

        assertThat(loaded.isEncrypted()).isTrue();
        assertThat(loaded.getPublicKeyComment()).isEqualTo("coverage@example");
    }

    @Test
    void encryptedPuttyVersionThreeKeyAttemptsConfiguredKdf() {
        byte[] key = puttyPrivateKey("PuTTY-User-Key-File-3", "ssh-rsa");

        assertThatThrownBy(() -> KeyPair.load(jsch, key, null))
                .isInstanceOf(JSchException.class)
                .hasMessageContaining("initWithPPKv3Header");
    }

    private static KeyPair loadLegacyPrivateKeyWithDekInfo(String cipherName, int ivHexLength)
            throws JSchException {
        String pem = new String(writeLegacyPrivateKey(PASSPHRASE), StandardCharsets.US_ASCII);
        String iv = "0".repeat(ivHexLength);
        String rewrittenPem = pem.replaceFirst("DEK-Info: DES-EDE3-CBC,[0-9A-F]+", "DEK-Info: "
                + cipherName + "," + iv);

        return KeyPair.load(jsch, rewrittenPem.getBytes(StandardCharsets.US_ASCII), null);
    }

    private static byte[] writeLegacyPrivateKey(byte[] passphrase) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyPair.writePrivateKey(out, passphrase);
        return out.toByteArray();
    }

    private static byte[] puttyPrivateKey(String versionHeader, String keyType) {
        String publicKeyBlob = publicKeyBlob();
        String privateKeyBlob = Base64.getEncoder().encodeToString(new byte[32]);
        String ppk = """
                %s: %s
                Encryption: aes256-cbc
                Comment: coverage@example
                Public-Lines: 1
                %s
                Private-Lines: 1
                %s
                Key-Derivation: bcrypt
                Private-MAC: 0000000000000000000000000000000000000000
                """.formatted(versionHeader, keyType, publicKeyBlob, privateKeyBlob);
        return ppk.getBytes(StandardCharsets.US_ASCII);
    }

    private static String publicKeyBlob() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        keyPair.writePublicKey(out, "coverage@example");
        String publicKey = new String(out.toByteArray(), StandardCharsets.US_ASCII).trim();
        String[] parts = publicKey.split(" ");
        assertThat(parts).hasSize(3);
        return parts[1];
    }
}
