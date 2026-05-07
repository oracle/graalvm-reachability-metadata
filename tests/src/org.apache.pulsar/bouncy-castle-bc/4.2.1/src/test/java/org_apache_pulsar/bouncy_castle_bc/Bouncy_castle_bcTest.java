/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.bouncy_castle_bc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.pulsar.bcloader.BouncyCastleLoader;
import org.apache.pulsar.common.util.BCLoader;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class Bouncy_castle_bcTest {
    private static final String PROVIDER_NAME = "BC";

    @Test
    void loaderRegistersAndReturnsBouncyCastleProvider() {
        Provider provider = new BouncyCastleLoader().getProvider();

        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo(PROVIDER_NAME);
        assertThat(provider).isInstanceOf(BouncyCastleProvider.class);
        assertThat(Security.getProvider(PROVIDER_NAME)).isSameAs(provider);
        assertThat(BouncyCastleLoader.provider).isSameAs(provider);
        assertThat(provider.getInfo()).contains("BouncyCastle");
    }

    @Test
    void loaderSatisfiesPulsarBcLoaderContract() {
        BCLoader loader = new BouncyCastleLoader();

        assertThat(loader.getProvider()).isSameAs(Security.getProvider(PROVIDER_NAME));
        assertThatCode(() -> MessageDigest.getInstance("SHA-256", loader.getProvider()))
                .doesNotThrowAnyException();
    }

    @Test
    void providerAdvertisesCoreCryptographicServices() {
        Provider provider = new BouncyCastleLoader().getProvider();

        assertThat(provider.getService("MessageDigest", "SHA-256")).isNotNull();
        assertThat(provider.getService("Mac", "HMACSHA256")).isNotNull();
        assertThat(provider.getService("KeyPairGenerator", "RSA")).isNotNull();
        assertThat(provider.getService("Signature", "SHA256WITHRSA")).isNotNull();
        assertThat(provider.getService("Cipher", "AES")).isNotNull();
    }

    @Test
    void messageDigestAndMacUseRegisteredProvider() throws Exception {
        Provider provider = new BouncyCastleLoader().getProvider();

        MessageDigest digest = MessageDigest.getInstance("SHA-256", provider);
        byte[] digestBytes = digest.digest("abc".getBytes(StandardCharsets.UTF_8));

        Mac mac = Mac.getInstance("HmacSHA256", provider);
        mac.init(new SecretKeySpec("key".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] macBytes = mac.doFinal(
                "The quick brown fox jumps over the lazy dog".getBytes(StandardCharsets.UTF_8));

        assertThat(hex(digestBytes))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hex(macBytes))
                .isEqualTo("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8");
        assertThat(digest.getProvider()).isSameAs(provider);
        assertThat(mac.getProvider()).isSameAs(provider);
    }

    @Test
    void rsaSignatureGeneratedByBouncyCastleVerifiesWithBouncyCastle() throws Exception {
        Provider provider = new BouncyCastleLoader().getProvider();
        byte[] message = "pulsar-bouncy-castle-loader".getBytes(StandardCharsets.UTF_8);

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", provider);
        generator.initialize(1024);
        KeyPair keyPair = generator.generateKeyPair();

        Signature signer = Signature.getInstance("SHA256withRSA", provider);
        signer.initSign(keyPair.getPrivate());
        signer.update(message);
        byte[] signature = signer.sign();

        Signature verifier = Signature.getInstance("SHA256withRSA", provider);
        verifier.initVerify(keyPair.getPublic());
        verifier.update(message);

        assertThat(signature).isNotEmpty();
        assertThat(verifier.verify(signature)).isTrue();
        assertThat(signer.getProvider()).isSameAs(provider);
        assertThat(verifier.getProvider()).isSameAs(provider);
    }

    @Test
    void aesGcmEncryptionRoundTripUsesBouncyCastleProvider() throws Exception {
        Provider provider = new BouncyCastleLoader().getProvider();
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);
        byte[] iv = "123456789012".getBytes(StandardCharsets.UTF_8);
        byte[] aad = "pulsar".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "native-image compatible encryption".getBytes(StandardCharsets.UTF_8);

        Cipher encrypt = Cipher.getInstance("AES/GCM/NoPadding", provider);
        encrypt.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        encrypt.updateAAD(aad);
        byte[] ciphertext = encrypt.doFinal(plaintext);

        Cipher decrypt = Cipher.getInstance("AES/GCM/NoPadding", provider);
        decrypt.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        decrypt.updateAAD(aad);
        byte[] decrypted = decrypt.doFinal(ciphertext);

        assertThat(ciphertext).isNotEqualTo(plaintext);
        assertThat(decrypted).isEqualTo(plaintext);
        assertThat(encrypt.getProvider()).isSameAs(provider);
        assertThat(decrypt.getProvider()).isSameAs(provider);
    }

    @Test
    void ecdhKeyAgreementDerivesMatchingSecretsWithBouncyCastleProvider() throws Exception {
        Provider provider = new BouncyCastleLoader().getProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", provider);
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair aliceKeyPair = generator.generateKeyPair();
        KeyPair bobKeyPair = generator.generateKeyPair();

        KeyAgreement aliceAgreement = KeyAgreement.getInstance("ECDH", provider);
        aliceAgreement.init(aliceKeyPair.getPrivate());
        aliceAgreement.doPhase(bobKeyPair.getPublic(), true);
        byte[] aliceSecret = aliceAgreement.generateSecret();

        KeyAgreement bobAgreement = KeyAgreement.getInstance("ECDH", provider);
        bobAgreement.init(bobKeyPair.getPrivate());
        bobAgreement.doPhase(aliceKeyPair.getPublic(), true);
        byte[] bobSecret = bobAgreement.generateSecret();

        assertThat(aliceSecret).isNotEmpty();
        assertThat(bobSecret).isEqualTo(aliceSecret);
        assertThat(aliceAgreement.getProvider()).isSameAs(provider);
        assertThat(bobAgreement.getProvider()).isSameAs(provider);
    }

    @Test
    void bcfksKeyStoreStoresAndLoadsSecretKeyWithBouncyCastleProvider() throws Exception {
        Provider provider = new BouncyCastleLoader().getProvider();
        char[] password = "keystore-password".toCharArray();
        SecretKey secretKey = new SecretKeySpec(
                "0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");

        KeyStore keyStore = KeyStore.getInstance("BCFKS", provider);
        keyStore.load(null, password);
        KeyStore.ProtectionParameter protection = new KeyStore.PasswordProtection(password);
        keyStore.setEntry("pulsar-secret", new KeyStore.SecretKeyEntry(secretKey), protection);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        keyStore.store(output, password);

        KeyStore reloadedKeyStore = KeyStore.getInstance("BCFKS", provider);
        reloadedKeyStore.load(new ByteArrayInputStream(output.toByteArray()), password);
        Key reloadedKey = reloadedKeyStore.getKey("pulsar-secret", password);

        assertThat(output.toByteArray()).isNotEmpty();
        assertThat(reloadedKeyStore.containsAlias("pulsar-secret")).isTrue();
        assertThat(reloadedKey).isInstanceOf(SecretKey.class);
        assertThat(reloadedKey.getAlgorithm()).isEqualTo("AES");
        assertThat(reloadedKey.getEncoded()).isEqualTo(secretKey.getEncoded());
        assertThat(keyStore.getProvider()).isSameAs(provider);
        assertThat(reloadedKeyStore.getProvider()).isSameAs(provider);
    }

    @Test
    void serviceDescriptorNamesThePublicLoaderClass() throws IOException {
        try (InputStream stream = BouncyCastleLoader.class.getClassLoader()
                .getResourceAsStream("META-INF/services/bouncy-castle.yaml")) {
            assertThat(stream).isNotNull();
            String descriptor = new String(stream.readAllBytes(), StandardCharsets.UTF_8);

            assertThat(descriptor).contains(
                    "name: bouncy-castle",
                    "description: loader for Bouncy Castle provider",
                    "bcLoaderClass: org.apache.pulsar.bcloader.BouncyCastleLoader");
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
