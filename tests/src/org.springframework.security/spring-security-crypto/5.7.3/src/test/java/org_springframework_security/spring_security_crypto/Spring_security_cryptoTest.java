/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_security.spring_security_crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.codec.Utf8;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.BytesKeyGenerator;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder.SecretKeyFactoryAlgorithm;
import org.springframework.security.crypto.util.EncodingUtils;

public class Spring_security_cryptoTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String WRONG_PASSWORD = "correct horse battery staple?";
    private static final String SALT = "5c0744940b5c369b";

    @Test
    void bcryptPasswordEncoderEncodesMatchesAndDetectsWeakCost() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(BCryptVersion.$2B, 4);

        String encoded = encoder.encode(PASSWORD);

        assertThat(encoded).startsWith("$2b$04$").hasSize(60);
        assertThat(encoder.matches(PASSWORD, encoded)).isTrue();
        assertThat(encoder.matches(WRONG_PASSWORD, encoded)).isFalse();
        assertThat(encoder.upgradeEncoding(encoded)).isFalse();
        assertThat(new BCryptPasswordEncoder(BCryptVersion.$2B, 10).upgradeEncoding(encoded)).isTrue();
    }

    @Test
    void bcryptStaticApiGeneratesSaltHashesStringsAndHashesBytes() {
        SecureRandom random = new SecureRandom(new byte[] {1, 2, 3, 4, 5, 6, 7, 8 });
        String salt = BCrypt.gensalt("$2a", 4, random);

        String stringHash = BCrypt.hashpw(PASSWORD, salt);
        String byteHash = BCrypt.hashpw(PASSWORD.getBytes(StandardCharsets.UTF_8), salt);

        assertThat(salt).startsWith("$2a$04$").hasSize(29);
        assertThat(stringHash).startsWith(salt).hasSize(60);
        assertThat(byteHash).isEqualTo(stringHash);
        assertThat(BCrypt.checkpw(PASSWORD, stringHash)).isTrue();
        assertThat(BCrypt.checkpw(PASSWORD.getBytes(StandardCharsets.UTF_8), stringHash)).isTrue();
        assertThat(BCrypt.checkpw(WRONG_PASSWORD, stringHash)).isFalse();
    }

    @Test
    void pbkdf2PasswordEncoderSupportsHexAndBase64EncodedHashes() {
        Pbkdf2PasswordEncoder hexEncoder = new Pbkdf2PasswordEncoder("application-wide-secret", 8, 64, 1_000);
        hexEncoder.setAlgorithm(SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);

        String hexEncoded = hexEncoder.encode(PASSWORD);

        assertThat(hexEncoded).matches("[0-9a-f]+");
        assertThat(hexEncoder.matches(PASSWORD, hexEncoded)).isTrue();
        assertThat(hexEncoder.matches(WRONG_PASSWORD, hexEncoded)).isFalse();

        Pbkdf2PasswordEncoder base64Encoder = new Pbkdf2PasswordEncoder("application-wide-secret", 8, 64, 1_000);
        base64Encoder.setAlgorithm(SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA512);
        base64Encoder.setEncodeHashAsBase64(true);

        String base64Encoded = base64Encoder.encode(PASSWORD);

        assertThat(base64Encoded).matches("[A-Za-z0-9+/]+={0,2}");
        assertThat(base64Encoder.matches(PASSWORD, base64Encoded)).isTrue();
        assertThat(base64Encoder.matches(PASSWORD, hexEncoded)).isFalse();
    }

    @Test
    void delegatingPasswordEncoderUsesCurrentIdAndVerifiesExplicitLegacyId() {
        Pbkdf2PasswordEncoder pbkdf2 = new Pbkdf2PasswordEncoder("delegating-secret", 8, 64, 1_000);
        pbkdf2.setAlgorithm(SecretKeyFactoryAlgorithm.PBKDF2WithHmacSHA256);
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder(4));
        encoders.put("pbkdf2", pbkdf2);
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("bcrypt", encoders);

        String currentEncoded = delegating.encode(PASSWORD);
        String legacyEncoded = "{pbkdf2}" + pbkdf2.encode(PASSWORD);

        assertThat(currentEncoded).startsWith("{bcrypt}$2a$04$");
        assertThat(delegating.matches(PASSWORD, currentEncoded)).isTrue();
        assertThat(delegating.matches(PASSWORD, legacyEncoded)).isTrue();
        assertThat(delegating.matches(WRONG_PASSWORD, legacyEncoded)).isFalse();
        assertThat(delegating.upgradeEncoding(legacyEncoded)).isTrue();
        assertThatThrownBy(() -> delegating.matches(PASSWORD, "{unknown}" + currentEncoded))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PasswordEncoder mapped for the id \"unknown\"");
    }

    @Test
    void keyGeneratorsCreateRandomSharedAndHexStringKeys() {
        BytesKeyGenerator randomGenerator = KeyGenerators.secureRandom(32);
        byte[] firstRandomKey = randomGenerator.generateKey();
        byte[] secondRandomKey = randomGenerator.generateKey();

        assertThat(randomGenerator.getKeyLength()).isEqualTo(32);
        assertThat(firstRandomKey).hasSize(32);
        assertThat(secondRandomKey).hasSize(32);
        assertThat(Arrays.equals(firstRandomKey, secondRandomKey)).isFalse();

        BytesKeyGenerator sharedGenerator = KeyGenerators.shared(16);
        byte[] sharedKey = sharedGenerator.generateKey();

        assertThat(sharedGenerator.getKeyLength()).isEqualTo(16);
        assertThat(sharedKey).hasSize(16);
        assertThat(sharedGenerator.generateKey()).containsExactly(sharedKey);

        StringKeyGenerator stringGenerator = KeyGenerators.string();

        assertThat(stringGenerator.generateKey()).matches("[0-9a-f]{16}");
    }

    @Test
    void base64StringKeyGeneratorProducesUrlSafeKeysWithoutPadding() {
        Base64StringKeyGenerator generator = new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 32);

        String key = generator.generateKey();

        assertThat(key).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void codecAndEncodingUtilitiesRoundTripTextAndByteRanges() {
        String text = "Spring Security crypto \u2615";
        byte[] utf8 = Utf8.encode(text);

        assertThat(Utf8.decode(utf8)).isEqualTo(text);
        assertThat(Hex.encode(new byte[] {0x00, 0x0f, (byte) 0xff }))
                .containsExactly('0', '0', '0', 'f', 'f', 'f');
        assertThat(Hex.decode("000fff")).containsExactly(0x00, 0x0f, (byte) 0xff);
        assertThat(EncodingUtils.concatenate(new byte[] {1, 2 }, new byte[] {3 }, new byte[] {4, 5 }))
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(EncodingUtils.subArray(new byte[] {10, 20, 30, 40, 50 }, 1, 4)).containsExactly(20, 30, 40);
        assertThatThrownBy(() -> Hex.decode("abc")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void textEncryptorsEncryptDecryptAndUseAuthenticatedMode() {
        TextEncryptor standardText = Encryptors.text("text-password", SALT);
        TextEncryptor authenticatedText = Encryptors.delux("text-password", SALT);
        String message = "token=abc123&scope=read write";

        String standardCiphertext = standardText.encrypt(message);
        String authenticatedCiphertext = authenticatedText.encrypt(message);

        assertThat(standardCiphertext).isNotEqualTo(message).matches("[0-9a-f]+");
        assertThat(authenticatedCiphertext).isNotEqualTo(message).matches("[0-9a-f]+");
        assertThat(standardText.decrypt(standardCiphertext)).isEqualTo(message);
        assertThat(authenticatedText.decrypt(authenticatedCiphertext)).isEqualTo(message);
        assertThatThrownBy(() -> authenticatedText.decrypt(standardCiphertext)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void queryableTextEncryptorProducesStableCiphertextForLookupValues() {
        TextEncryptor firstEncryptor = Encryptors.queryableText("lookup-password", SALT);
        TextEncryptor secondEncryptor = Encryptors.queryableText("lookup-password", SALT);
        String lookupValue = "customer-12345";

        String firstCiphertext = firstEncryptor.encrypt(lookupValue);
        String repeatedCiphertext = firstEncryptor.encrypt(lookupValue);
        String secondEncryptorCiphertext = secondEncryptor.encrypt(lookupValue);
        String differentCiphertext = firstEncryptor.encrypt("customer-67890");

        assertThat(firstCiphertext).isNotEqualTo(lookupValue).matches("[0-9a-f]+");
        assertThat(repeatedCiphertext).isEqualTo(firstCiphertext);
        assertThat(secondEncryptorCiphertext).isEqualTo(firstCiphertext);
        assertThat(differentCiphertext).isNotEqualTo(firstCiphertext);
        assertThat(secondEncryptor.decrypt(firstCiphertext)).isEqualTo(lookupValue);
    }

    @Test
    void bytesEncryptorsRoundTripBinaryPayloadsWithCbcAndGcm() {
        BytesEncryptor standard = Encryptors.standard("bytes-password", SALT);
        BytesEncryptor stronger = Encryptors.stronger("bytes-password", SALT);
        byte[] payload = new byte[] {0, 1, 2, 3, 4, 5, 127, (byte) 128, (byte) 255 };

        byte[] standardCiphertext = standard.encrypt(payload);
        byte[] strongerCiphertext = stronger.encrypt(payload);

        assertThat(Arrays.equals(standardCiphertext, payload)).isFalse();
        assertThat(Arrays.equals(strongerCiphertext, payload)).isFalse();
        assertThat(standardCiphertext).hasSizeGreaterThan(payload.length);
        assertThat(strongerCiphertext).hasSizeGreaterThan(payload.length);
        assertThat(standard.decrypt(standardCiphertext)).containsExactly(payload);
        assertThat(stronger.decrypt(strongerCiphertext)).containsExactly(payload);
        assertThatThrownBy(() -> stronger.decrypt(standardCiphertext)).isInstanceOf(RuntimeException.class);
    }
}
