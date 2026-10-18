/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.encrypt.RsaAlgorithm;
import org.springframework.security.crypto.encrypt.RsaRawEncryptor;
import org.springframework.security.crypto.encrypt.RsaSecretEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * PEM key text parsing through the `String` constructors of `RsaSecretEncryptor` and
 * `RsaRawEncryptor`, which read PKCS#1 `RSA PRIVATE KEY`, X.509 `PUBLIC KEY`, and PKCS#1
 * `RSA PUBLIC KEY` blocks.
 *
 * A containerised service gets its RSA key pair as PEM files in a mounted secret volume,
 * the way `openssl genrsa` and `openssl rsa -pubout` write them, instead of a keystore.
 * The service builds its decryptor from the private key text, the deployment pipeline
 * builds an encrypt-only encryptor from the public key text, and both agree on the
 * `ssh-rsa` fingerprint they publish.
 */
public class MountedPemKeySecretsTest {

    @TempDir
    static Path secretsMount;

    private static KeyPair issuedKeyPair;
    private static String privateKeyPem;
    private static String publicKeyPem;
    private static String rsaPublicKeyPem;
    private static String pkcs8PrivateKeyPem;

    @BeforeAll
    static void provisionSecretVolume() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        issuedKeyPair = generator.generateKeyPair();
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(issuedKeyPair.getPrivate().getEncoded());
        privateKeyPem = pem("RSA PRIVATE KEY", privateKeyInfo.parsePrivateKey().toASN1Primitive().getEncoded());
        publicKeyPem = pem("PUBLIC KEY", issuedKeyPair.getPublic().getEncoded());
        SubjectPublicKeyInfo publicKeyInfo =
                SubjectPublicKeyInfo.getInstance(issuedKeyPair.getPublic().getEncoded());
        rsaPublicKeyPem = pem("RSA PUBLIC KEY", publicKeyInfo.getPublicKeyData().getBytes());
        pkcs8PrivateKeyPem = pem("PRIVATE KEY", issuedKeyPair.getPrivate().getEncoded());
        Files.writeString(secretsMount.resolve("tls.key"), privateKeyPem);
        Files.writeString(secretsMount.resolve("tls.pub"), publicKeyPem);
    }

    @Test
    void serviceDecryptsWhatThePipelineEncryptsWithMountedPemFiles() throws IOException {
        MountedKeyring keyring = new MountedKeyring(secretsMount);
        TextEncryptor service = keyring.decryptor();
        RsaSecretEncryptor pipeline = keyring.encryptOnly();

        String sealed = pipeline.encrypt("jdbc-password-0b7f");
        assertNotEquals("jdbc-password-0b7f", sealed);
        assertEquals("jdbc-password-0b7f", service.decrypt(sealed));
        assertFalse(pipeline.canDecrypt());
        assertThrows(IllegalStateException.class, () -> pipeline.decrypt(sealed));

        byte[] token = "api-token:7f3c9a".getBytes(StandardCharsets.UTF_8);
        RsaSecretEncryptor bytesService = new RsaSecretEncryptor(privateKeyPem);
        assertTrue(bytesService.canDecrypt());
        assertArrayEquals(token, bytesService.decrypt(pipeline.encrypt(token)));
    }

    @Test
    void privateAndPublicPemPublishTheSameSshKey() {
        RsaSecretEncryptor service = new RsaSecretEncryptor(privateKeyPem);
        RsaSecretEncryptor pipeline = new RsaSecretEncryptor(publicKeyPem);
        RsaSecretEncryptor fromKeyPair = new RsaSecretEncryptor(issuedKeyPair);

        String published = service.getPublicKey();
        assertTrue(published.startsWith("ssh-rsa AAAAB3NzaC1yc2E"), published);
        assertEquals(published, pipeline.getPublicKey());
        assertEquals(published, fromKeyPair.getPublicKey());

        RsaSecretEncryptor fromSsh = new RsaSecretEncryptor(published);
        assertEquals("rotate-me", service.decrypt(fromSsh.encrypt("rotate-me")));
    }

    @Test
    void indentedPemFromATemplatedEnvironmentVariableStillParses() {
        String helmIndented = "    " + privateKeyPem.strip().replace("\n", "\n    ") + "\n";
        RsaRawEncryptor service = new RsaRawEncryptor(helmIndented);
        RsaRawEncryptor pipeline = new RsaRawEncryptor(publicKeyPem);

        assertEquals(new RsaRawEncryptor(issuedKeyPair).getPublicKey(), service.getPublicKey());
        assertEquals("smtp-relay-secret", service.decrypt(pipeline.encrypt("smtp-relay-secret")));
    }

    @Test
    void pkcs1PublicKeyPemEncryptsForTheService() {
        RsaRawEncryptor legacyTooling = new RsaRawEncryptor(rsaPublicKeyPem);
        RsaRawEncryptor service = new RsaRawEncryptor(privateKeyPem);

        assertEquals(service.getPublicKey(), legacyTooling.getPublicKey());
        assertEquals("ldap-bind-pass", service.decrypt(legacyTooling.encrypt("ldap-bind-pass")));

        RsaSecretEncryptor secretTooling = new RsaSecretEncryptor(rsaPublicKeyPem);
        assertEquals("ldap-bind-pass", new RsaSecretEncryptor(privateKeyPem).decrypt(
                secretTooling.encrypt("ldap-bind-pass")));
    }

    @Test
    void saltedOaepSettingsApplyToPemKeys() {
        RsaSecretEncryptor service = new RsaSecretEncryptor(privateKeyPem, RsaAlgorithm.OAEP, "a1b2c3d4");
        RsaSecretEncryptor pipeline = new RsaSecretEncryptor(publicKeyPem, RsaAlgorithm.OAEP, "a1b2c3d4");
        RsaSecretEncryptor otherSalt = new RsaSecretEncryptor(privateKeyPem, RsaAlgorithm.OAEP, "deadbeef");

        String sealed = pipeline.encrypt("queue-credentials");
        assertEquals("queue-credentials", service.decrypt(sealed));
        assertThrows(IllegalStateException.class, () -> otherSalt.decrypt(sealed));
    }

    @Test
    void unsupportedKeyTextIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new RsaSecretEncryptor(pkcs8PrivateKeyPem));
        IllegalArgumentException notPem = assertThrows(IllegalArgumentException.class,
                () -> new RsaSecretEncryptor("not-a-key"));
        assertEquals("String is not PEM encoded data, nor a public key encoded for ssh", notPem.getMessage());
    }

    private static String pem(String type, byte[] der) throws IOException {
        StringWriter out = new StringWriter();
        try (PemWriter writer = new PemWriter(out)) {
            writer.writeObject(new PemObject(type, der));
        }
        return out.toString();
    }

    /// Builds encryptors from the PEM files a secret volume mounts into the container.
    private static final class MountedKeyring {
        private final Path mount;

        MountedKeyring(Path mount) {
            this.mount = mount;
        }

        TextEncryptor decryptor() throws IOException {
            return new RsaSecretEncryptor(Files.readString(mount.resolve("tls.key")));
        }

        RsaSecretEncryptor encryptOnly() throws IOException {
            return new RsaSecretEncryptor(Files.readString(mount.resolve("tls.pub")));
        }
    }
}
