/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_cipher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.sonatype.plexus.components.cipher.Base64;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PBECipher;
import org.sonatype.plexus.components.cipher.PlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipherException;

public class Plexus_cipherTest {
    private static final String PASSPHRASE = "correct horse battery staple";

    @Test
    void defaultCipherEncryptsAndDecryptsTextWithUnicodeContent() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        String plaintext = "secret value with unicode \u03c0, emoji \ud83d\ude80, and\nmultiple lines";

        String encrypted = cipher.encrypt(plaintext, PASSPHRASE);
        String secondEncrypted = cipher.encrypt(plaintext, PASSPHRASE);

        assertThat(encrypted).isNotBlank().isNotEqualTo(plaintext);
        assertThat(secondEncrypted).isNotBlank().isNotEqualTo(plaintext);
        assertThat(Base64.isArrayByteBase64(encrypted.getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(cipher.decrypt(encrypted, PASSPHRASE)).isEqualTo(plaintext);
        assertThat(cipher.decrypt(secondEncrypted, PASSPHRASE)).isEqualTo(plaintext);
    }

    @Test
    void encryptedTextCannotBeRecoveredWithDifferentPassphrase() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        String plaintext = "passphrase protected secret";

        String encrypted = cipher.encrypt(plaintext, PASSPHRASE);

        try {
            String decrypted = cipher.decrypt(encrypted, "different " + PASSPHRASE);
            assertThat(decrypted).isNotEqualTo(plaintext);
        } catch (PlexusCipherException expected) {
            assertThat(expected).hasCauseInstanceOf(Exception.class);
        }
    }

    @Test
    void decoratedEncryptionRoundTripsAndSupportsDecoratorUtilities() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        String plaintext = "decorated secret";

        String decorated = cipher.encryptAndDecorate(plaintext, PASSPHRASE);
        String undecorated = cipher.unDecorate(decorated);

        assertThat(decorated).startsWith("{").endsWith("}");
        assertThat(cipher.isEncryptedString(decorated)).isTrue();
        assertThat(cipher.isEncryptedString("prefix " + decorated + " suffix")).isTrue();
        assertThat(cipher.unDecorate("prefix " + decorated + " suffix")).isEqualTo(undecorated);
        assertThat(cipher.decryptDecorated(decorated, PASSPHRASE)).isEqualTo(plaintext);
        assertThat(cipher.decryptDecorated(undecorated, PASSPHRASE)).isEqualTo(plaintext);
        assertThat(cipher.decorate("plain-token")).isEqualTo("{plain-token}");
        assertThat(cipher.unDecorate("{plain-token}")).isEqualTo("plain-token");
    }

    @Test
    void escapedDecorationDelimitersAreHandledWhenDetectingEncryptedValues() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();
        String escapedBraces = "\\{not encrypted\\}";
        String mixedBraces = "Comment {foo\\{inner secret\\}} trailing text with }";

        assertThat(cipher.isEncryptedString(escapedBraces)).isFalse();
        assertThat(cipher.isEncryptedString(mixedBraces)).isTrue();
        assertThat(cipher.unDecorate(mixedBraces)).isEqualTo("foo\\{inner secret\\}");
        assertThatExceptionOfType(PlexusCipherException.class)
                .isThrownBy(() -> cipher.unDecorate(escapedBraces))
                .withMessage("default.plexus.cipher.badEncryptedPassword");
    }

    @Test
    void cipherPreservesNullAndEmptyInputsWhereApiDefinesPassThrough() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();

        assertThat(cipher.encrypt(null, PASSPHRASE)).isNull();
        assertThat(cipher.decrypt(null, PASSPHRASE)).isNull();
        assertThat(cipher.decryptDecorated(null, PASSPHRASE)).isNull();
        assertThat(cipher.encrypt("", PASSPHRASE)).isEmpty();
        assertThat(cipher.decrypt("", PASSPHRASE)).isEmpty();
        assertThat(cipher.decryptDecorated("", PASSPHRASE)).isEmpty();
        assertThat(cipher.decorate(null)).isEqualTo("{}");
        assertThat(cipher.encryptAndDecorate(null, PASSPHRASE)).isEqualTo("{}");
        assertThat(cipher.isEncryptedString(null)).isFalse();
        assertThat(cipher.isEncryptedString("")).isFalse();
    }

    @Test
    void invalidDecoratedOrEncryptedValuesReportPlexusCipherExceptions() throws Exception {
        PlexusCipher cipher = new DefaultPlexusCipher();

        assertThatExceptionOfType(PlexusCipherException.class)
                .isThrownBy(() -> cipher.unDecorate("not decorated"))
                .withMessage("default.plexus.cipher.badEncryptedPassword");
        assertMalformedCiphertextFails(() -> cipher.decrypt("AA==", PASSPHRASE));
    }

    @Test
    void pbeCipherEncryptsToBase64AndDecryptsWithMatchingPassword() throws Exception {
        PBECipher cipher = new PBECipher();
        String plaintext = "direct PBE payload";

        String encrypted = cipher.encrypt64(plaintext, PASSPHRASE);

        assertThat(Base64.isArrayByteBase64(encrypted.getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(cipher.decrypt64(encrypted, PASSPHRASE)).isEqualTo(plaintext);
        assertMalformedCiphertextFails(() -> cipher.decrypt64("AA==", PASSPHRASE));
    }

    @Test
    void base64StaticMethodsEncodeDecodeAndValidateCommonBytePatterns() {
        byte[][] inputs = {
                new byte[0],
                "f".getBytes(StandardCharsets.US_ASCII),
                "fo".getBytes(StandardCharsets.US_ASCII),
                "foo".getBytes(StandardCharsets.US_ASCII),
                "hello world".getBytes(StandardCharsets.UTF_8),
                new byte[] {(byte) 0x00, (byte) 0x7f, (byte) 0x80, (byte) 0xff}
        };

        for (byte[] input : inputs) {
            byte[] encoded = Base64.encodeBase64(input);

            assertThat(encoded).isEqualTo(java.util.Base64.getEncoder().encode(input));
            assertThat(Base64.isArrayByteBase64(encoded)).isTrue();
            assertThat(Base64.decodeBase64(encoded)).isEqualTo(input);
        }

        assertThat(Base64.isBase64((byte) 'A')).isTrue();
        assertThat(Base64.isBase64((byte) '+')).isTrue();
        assertThat(Base64.isBase64((byte) '/')).isTrue();
        assertThat(Base64.isBase64((byte) '=')).isTrue();
        assertThat(Base64.isBase64((byte) '-')).isFalse();
        assertThat(Base64.isBase64((byte) 0xff)).isFalse();
        assertThat(Base64.isArrayByteBase64(" YWJj\r\n".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(Base64.isArrayByteBase64("YWJj!".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(Base64.decodeBase64("Y!W@J#j".getBytes(StandardCharsets.US_ASCII)))
                .isEqualTo("abc".getBytes(StandardCharsets.US_ASCII));
    }

    @Test
    void chunkedBase64UsesMimeLineSeparatorsAndStillDecodes() {
        byte[] input = new byte[120];
        for (int i = 0; i < input.length; i++) {
            input[i] = (byte) i;
        }

        byte[] chunked = Base64.encodeBase64Chunked(input);
        byte[] explicitlyChunked = Base64.encodeBase64(input, true);

        assertThat(chunked).isEqualTo(explicitlyChunked);
        assertThat(new String(chunked, StandardCharsets.US_ASCII)).contains("\r\n");
        assertThat(Base64.isArrayByteBase64(chunked)).isTrue();
        assertThat(Base64.decodeBase64(chunked)).isEqualTo(input);
    }

    @Test
    void base64InstanceMethodsAcceptOnlyByteArrays() {
        Base64 codec = new Base64();
        byte[] input = "instance codec".getBytes(StandardCharsets.UTF_8);
        byte[] encoded = codec.encode(input);

        assertThat(encoded).isEqualTo(Base64.encodeBase64(input));
        assertThat(codec.decode(encoded)).isEqualTo(input);
        assertThat((byte[]) codec.encode((Object) input)).isEqualTo(encoded);
        assertThat((byte[]) codec.decode((Object) encoded)).isEqualTo(input);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> codec.encode("not bytes"))
                .withMessage("Parameter supplied to Base64 encode is not a byte[]");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> codec.decode("not bytes"))
                .withMessage("Parameter supplied to Base64 decode is not a byte[]");
    }

    @Test
    void securityProviderDiscoveryReturnsAvailableServiceTypesAndImplementations() {
        String[] serviceTypes = DefaultPlexusCipher.getServiceTypes();
        String[] digestImplementations = DefaultPlexusCipher.getCryptoImpls("MessageDigest");
        String[] cipherImplementations = DefaultPlexusCipher.getCryptoImpls("Cipher");

        assertThat(serviceTypes).isNotEmpty().doesNotContainNull();
        assertThat(Arrays.asList(serviceTypes)).contains("MessageDigest", "Cipher");
        assertThat(digestImplementations).isNotEmpty().doesNotContainNull();
        assertThat(cipherImplementations).isNotEmpty().doesNotContainNull();
        assertThat(Arrays.asList(digestImplementations)).anySatisfy(implementation ->
                assertThat(implementation).containsIgnoringCase("SHA"));
        assertThat(Arrays.asList(cipherImplementations)).anySatisfy(implementation ->
                assertThat(implementation).containsIgnoringCase("AES"));
    }

    private static void assertMalformedCiphertextFails(ThrowingCallable callable) {
        assertThatThrownBy(callable::call)
                .isInstanceOfAny(PlexusCipherException.class, ArrayIndexOutOfBoundsException.class);
    }

    @FunctionalInterface
    private interface ThrowingCallable {
        void call() throws Exception;
    }
}
