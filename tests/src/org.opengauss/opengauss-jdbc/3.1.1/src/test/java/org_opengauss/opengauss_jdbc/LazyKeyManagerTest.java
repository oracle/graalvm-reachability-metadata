/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ssl.LazyKeyManager;
import org.postgresql.ssl.PrivateKeyFactory;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class LazyKeyManagerTest {
    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void loadsConfiguredPrivateKeyFactoryWhenPkcs8KeyCannotBeDecoded(@TempDir Path tempDir) throws Exception {
        byte[] privateKeyData = "custom encrypted private key".getBytes(StandardCharsets.UTF_8);
        RecordingPrivateKeyFactory.reset();
        Path certificateFile = writeCertificateFile(tempDir);
        Path privateKeyFile = tempDir.resolve("client.key");
        Files.write(privateKeyFile, privateKeyData);

        LazyKeyManager keyManager = new LazyKeyManager(certificateFile.toString(), privateKeyFile.toString(),
                passwordCallbackHandler(), false, RecordingPrivateKeyFactory.class.getName());

        PrivateKey privateKey = keyManager.getPrivateKey("user");
        keyManager.throwKeyManagerException();

        assertThat(privateKey).isNotNull();
        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(RecordingPrivateKeyFactory.keyData).containsExactly(privateKeyData);
        assertThat(RecordingPrivateKeyFactory.password).containsExactly(PASSWORD);
    }

    private static Path writeCertificateFile(Path tempDir) throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        X509Certificate certificate = createCertificate(keyPair);
        Path certificateFile = tempDir.resolve("client.crt");
        Files.write(certificateFile, certificate.getEncoded());
        return certificateFile;
    }

    private static X509Certificate createCertificate(KeyPair keyPair) throws Exception {
        Instant now = Instant.now();
        X500Name subject = new X500Name("CN=LazyKeyManagerTest");
        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                Date.from(now.minusSeconds(60)), Date.from(now.plusSeconds(600)), subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(certificateBuilder.build(signer));
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return keyPairGenerator.generateKeyPair();
    }

    private static CallbackHandler passwordCallbackHandler() {
        return callbacks -> {
            for (Callback callback : callbacks) {
                PasswordCallback passwordCallback = (PasswordCallback) callback;
                passwordCallback.setPassword(PASSWORD);
            }
        };
    }

    public static final class RecordingPrivateKeyFactory implements PrivateKeyFactory {
        private static byte[] keyData;
        private static char[] password;

        public RecordingPrivateKeyFactory() {
        }

        static void reset() {
            keyData = null;
            password = null;
        }

        @Override
        public PrivateKey getPrivateKeyFromEncryptedKey(byte[] data, PasswordCallback pwdcb) throws Exception {
            keyData = Arrays.copyOf(data, data.length);
            password = Arrays.copyOf(pwdcb.getPassword(), pwdcb.getPassword().length);
            return generateRsaKeyPair().getPrivate();
        }
    }
}
