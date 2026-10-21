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

import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.KeyStoreKeyFactory;
import org.springframework.security.crypto.encrypt.RsaAlgorithm;
import org.springframework.security.crypto.encrypt.RsaRawEncryptor;
import org.springframework.security.crypto.encrypt.RsaSecretEncryptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Asymmetric encryption of configuration secrets through `RsaSecretEncryptor` and
 * `RsaRawEncryptor`, keyed from a PKCS12 keystore that `KeyStoreKeyFactory` loads.
 *
 * A configuration service keeps its RSA key pair in a keystore and publishes only the
 * public key. The deployment pipeline encrypts database passwords and API tokens with
 * that public key and commits them as `{cipher}` property values; at startup the
 * service loads the private key from the keystore and resolves every encrypted value,
 * while a pipeline that holds only the public key can write secrets but never read them.
 */
public class EncryptedConfigPropertiesTest {

    private static final String ALIAS = "config-server";
    private static final char[] STORE_PASSWORD = "keystore-secret".toCharArray();

    @TempDir
    static Path keystoreDir;

    private static Resource keystore;
    private static KeyPair issuedKeyPair;
    private static X509Certificate certificate;

    @BeforeAll
    static void provisionKeystore() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        issuedKeyPair = generator.generateKeyPair();
        Date notBefore = new Date(System.currentTimeMillis() - 60_000L);
        Date notAfter = new Date(notBefore.getTime() + 365L * 24 * 60 * 60 * 1000);
        X500Name subject = new X500Name("CN=config-server, O=Example");
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject, BigInteger.ONE,
                notBefore, notAfter, subject, issuedKeyPair.getPublic());
        certificate = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuedKeyPair.getPrivate())));

        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, null);
        store.setKeyEntry(ALIAS, issuedKeyPair.getPrivate(), STORE_PASSWORD, new Certificate[] {certificate});
        Path file = keystoreDir.resolve("config-server.p12");
        try (OutputStream out = Files.newOutputStream(file)) {
            store.store(out, STORE_PASSWORD);
        }
        keystore = new FileSystemResource(file);
    }

    @Test
    void loadsTheServiceKeyPairFromAPkcs12Keystore() {
        KeyStoreKeyFactory factory = new KeyStoreKeyFactory(keystore, STORE_PASSWORD, "PKCS12");

        KeyPair loaded = factory.getKeyPair(ALIAS);
        assertEquals(issuedKeyPair.getPublic(), loaded.getPublic());
        assertEquals(certificate.getPublicKey(), loaded.getPublic());
        RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) loaded.getPrivate();
        assertEquals(((RSAPublicKey) loaded.getPublic()).getModulus(), privateKey.getModulus());
        assertEquals(2048, privateKey.getModulus().bitLength());

        KeyPair withExplicitKeyPassword = factory.getKeyPair(ALIAS, STORE_PASSWORD);
        assertEquals(loaded.getPublic(), withExplicitKeyPassword.getPublic());

        KeyStoreKeyFactory wrongPassword = new KeyStoreKeyFactory(keystore, "guess".toCharArray(), "PKCS12");
        assertThrows(IllegalStateException.class, () -> wrongPassword.getKeyPair(ALIAS));
    }

    @Test
    void pipelineEncryptsWithThePublishedKeyAndOnlyTheServiceDecrypts() {
        RsaSecretEncryptor service = new RsaSecretEncryptor(serviceKeyPair());
        String published = service.getPublicKey();
        assertTrue(published.startsWith("ssh-rsa AAAAB3NzaC1yc2E"), published);
        assertTrue(published.endsWith(" application"), published);

        RsaSecretEncryptor pipeline = new RsaSecretEncryptor(published);
        assertFalse(pipeline.canDecrypt());
        assertTrue(service.canDecrypt());
        assertEquals(published, pipeline.getPublicKey());

        String first = pipeline.encrypt("p0stgres-Pa55");
        String second = pipeline.encrypt("p0stgres-Pa55");
        assertNotEquals(first, second, "every value wraps a fresh random secret");
        assertEquals("p0stgres-Pa55", service.decrypt(first));
        assertEquals("p0stgres-Pa55", service.decrypt(second));

        IllegalStateException readByPipeline = assertThrows(IllegalStateException.class,
                () -> pipeline.decrypt(first));
        assertEquals("Encryptor is not configured for decryption", readByPipeline.getMessage());

        String certificateBundle = "-----BEGIN CERTIFICATE-----\n" + "MIIB".repeat(1200) + "\n-----END CERTIFICATE-----";
        assertEquals(certificateBundle, service.decrypt(pipeline.encrypt(certificateBundle)),
                "the envelope carries values far larger than one RSA block");
    }

    @Test
    void resolvesCipherPropertiesAtStartup() {
        TextEncryptor pipeline = new RsaSecretEncryptor(certificate.getPublicKey());
        Map<String, String> committed = new LinkedHashMap<>();
        committed.put("spring.datasource.url", "jdbc:postgresql://db:5432/orders");
        committed.put("spring.datasource.username", "orders");
        committed.put("spring.datasource.password", "{cipher}" + pipeline.encrypt("s3cr3t-db"));
        committed.put("payments.api-token", "{cipher}" + pipeline.encrypt("tok_live_8f3a"));
        committed.put("mail.from", "noreply@example.com");

        assertFalse(committed.get("spring.datasource.password").contains("s3cr3t-db"));

        ConfigResolver resolver = new ConfigResolver(new RsaSecretEncryptor(serviceKeyPair()));
        Map<String, String> resolved = resolver.resolve(committed);

        assertEquals(committed.keySet(), resolved.keySet());
        assertEquals("jdbc:postgresql://db:5432/orders", resolved.get("spring.datasource.url"));
        assertEquals("orders", resolved.get("spring.datasource.username"));
        assertEquals("s3cr3t-db", resolved.get("spring.datasource.password"));
        assertEquals("tok_live_8f3a", resolved.get("payments.api-token"));
        assertEquals("noreply@example.com", resolved.get("mail.from"));

        Map<String, String> foreign = Map.of("x.secret", "{cipher}" + rotatedEncryptor().encrypt("other-env"));
        assertThrows(IllegalStateException.class, () -> resolver.resolve(foreign),
                "a value encrypted for another environment's key never resolves");
    }

    @Test
    void hardensTheEnvelopeWithOaepGcmAndASaltPerEnvironment() {
        KeyPair keyPair = serviceKeyPair();
        RsaSecretEncryptor production = new RsaSecretEncryptor(keyPair, RsaAlgorithm.OAEP, "a1b2c3d4", true);
        String stored = production.encrypt("prod-signing-key");

        RsaSecretEncryptor sameSettings = new RsaSecretEncryptor(keyPair, RsaAlgorithm.OAEP, "a1b2c3d4", true);
        assertEquals("prod-signing-key", sameSettings.decrypt(stored));

        RsaSecretEncryptor pipeline = new RsaSecretEncryptor(keyPair.getPublic(), RsaAlgorithm.OAEP, "a1b2c3d4", true);
        assertEquals("prod-signing-key", production.decrypt(pipeline.encrypt("prod-signing-key")));

        RsaSecretEncryptor staging = new RsaSecretEncryptor(keyPair, RsaAlgorithm.OAEP, "deadbeef", true);
        assertThrows(IllegalStateException.class, () -> staging.decrypt(stored));
        RsaSecretEncryptor withoutGcm = new RsaSecretEncryptor(keyPair, RsaAlgorithm.OAEP, "a1b2c3d4", false);
        // CBC padding of the GCM body passes by chance about once in 256 runs, so only the
        // secret is asserted unreadable, not that decryption throws.
        String readWithoutGcm;
        try {
            readWithoutGcm = withoutGcm.decrypt(stored);
        } catch (IllegalStateException rejected) {
            readWithoutGcm = null;
        }
        assertNotEquals("prod-signing-key", readWithoutGcm);
        RsaSecretEncryptor pkcs1 = new RsaSecretEncryptor(keyPair, RsaAlgorithm.DEFAULT, "a1b2c3d4", true);
        assertThrows(IllegalStateException.class, () -> pkcs1.decrypt(stored));

        assertEquals("RSA/ECB/OAEPPadding", RsaAlgorithm.OAEP.getJceName());
        assertEquals("RSA", RsaAlgorithm.DEFAULT.getJceName());
    }

    @Test
    void sealsShortTokensWithRawRsaBlocks() {
        KeyPair keyPair = serviceKeyPair();
        RsaRawEncryptor service = new RsaRawEncryptor(keyPair);
        int block = RsaRawEncryptor.getByteLength((RSAPublicKey) keyPair.getPublic());
        assertEquals(256, block);

        RsaRawEncryptor pipeline = new RsaRawEncryptor(keyPair.getPublic());
        assertEquals(service.getPublicKey(), pipeline.getPublicKey());
        String sealed = pipeline.encrypt("webhook-hmac-key");
        assertEquals("webhook-hmac-key", service.decrypt(sealed));
        IllegalStateException readByPipeline = assertThrows(IllegalStateException.class,
                () -> pipeline.decrypt(sealed));
        assertEquals("Private key must be provided for decryption", readByPipeline.getMessage());

        int pkcs1Max = RsaAlgorithm.DEFAULT.getMaxLength();
        assertEquals(block, service.encrypt(new byte[pkcs1Max]).length);
        assertEquals(2 * block, service.encrypt(new byte[pkcs1Max + 1]).length, "longer input spans two blocks");
        byte[] licence = Utf8.encode("licence:".repeat(40));
        assertArrayEquals(licence, service.decrypt(service.encrypt(licence)));

        RsaRawEncryptor oaep = new RsaRawEncryptor(keyPair, RsaAlgorithm.OAEP);
        int oaepMax = RsaAlgorithm.OAEP.getMaxLength();
        assertEquals(block, oaep.encrypt(new byte[oaepMax]).length);
        assertEquals(2 * block, oaep.encrypt(new byte[oaepMax + 1]).length);
        assertEquals("webhook-hmac-key", oaep.decrypt(oaep.encrypt("webhook-hmac-key")));

        assertThrows(IllegalStateException.class, () -> new RsaRawEncryptor(rotatedKeyPair()).decrypt(sealed));
    }

    private static KeyPair serviceKeyPair() {
        return new KeyStoreKeyFactory(keystore, STORE_PASSWORD, "PKCS12").getKeyPair(ALIAS);
    }

    private static KeyPair rotatedKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static TextEncryptor rotatedEncryptor() {
        return new RsaSecretEncryptor(rotatedKeyPair().getPublic());
    }

    /// Replaces every `{cipher}` property value with its decrypted form.
    private static final class ConfigResolver {
        private static final String PREFIX = "{cipher}";
        private final TextEncryptor encryptor;

        ConfigResolver(TextEncryptor encryptor) {
            this.encryptor = encryptor;
        }

        Map<String, String> resolve(Map<String, String> properties) {
            Map<String, String> resolved = new LinkedHashMap<>();
            properties.forEach((name, value) -> resolved.put(name, value.startsWith(PREFIX)
                    ? encryptor.decrypt(value.substring(PREFIX.length()))
                    : value));
            return resolved;
        }
    }
}
