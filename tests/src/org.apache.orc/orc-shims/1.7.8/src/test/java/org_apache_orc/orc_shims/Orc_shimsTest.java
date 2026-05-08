/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_orc.orc_shims;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Arrays;
import java.util.Random;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.orc.EncryptionAlgorithm;
import org.apache.orc.impl.HadoopShims;
import org.apache.orc.impl.HadoopShims.DirectCompressionType;
import org.apache.orc.impl.HadoopShims.KeyMetadata;
import org.apache.orc.impl.HadoopShims.KeyProviderKind;
import org.apache.orc.impl.HadoopShimsCurrent;
import org.apache.orc.impl.HadoopShimsPre2_6;
import org.apache.orc.impl.HadoopShimsPre2_7;
import org.apache.orc.impl.KeyProvider;
import org.apache.orc.impl.LocalKey;
import org.junit.jupiter.api.Test;

public class Orc_shimsTest {
    @Test
    void encryptionAlgorithmsExposeStablePropertiesAndCreateUsableCiphers() throws Exception {
        assertThat(EncryptionAlgorithm.values())
                .containsExactly(EncryptionAlgorithm.AES_CTR_128, EncryptionAlgorithm.AES_CTR_256);

        assertAlgorithmRoundTrip(EncryptionAlgorithm.AES_CTR_128, 1, 16, "AES128");
        assertAlgorithmRoundTrip(EncryptionAlgorithm.AES_CTR_256, 2, 32, "AES256");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EncryptionAlgorithm.fromSerialization(99))
                .withMessageContaining("Unknown code in encryption algorithm 99");
    }

    @Test
    void localKeyWrapsDecryptedKeyAndPreservesEncryptedBytes() {
        byte[] decryptedBytes = repeatByte(EncryptionAlgorithm.AES_CTR_128.keyLength(), (byte) 7);
        byte[] encryptedBytes = new byte[] {11, 12, 13, 14};

        LocalKey localKey = new LocalKey(EncryptionAlgorithm.AES_CTR_128, decryptedBytes, encryptedBytes);

        assertThat(localKey.getEncryptedKey()).containsExactly(encryptedBytes);
        assertThat(localKey.getDecryptedKey().getAlgorithm()).isEqualTo("AES");
        assertThat(localKey.getDecryptedKey().getEncoded()).containsExactly(decryptedBytes);

        Key replacementKey = new SecretKeySpec(
                repeatByte(EncryptionAlgorithm.AES_CTR_128.keyLength(), (byte) 3), "AES");
        localKey.setDecryptedKey(replacementKey);
        assertThat(localKey.getDecryptedKey()).isSameAs(replacementKey);
    }

    @Test
    void localKeyCanRepresentAnEncryptedOnlyKeyUntilDecryptionIsSupplied() {
        byte[] encryptedBytes = new byte[] {21, 22, 23};

        LocalKey localKey = new LocalKey(EncryptionAlgorithm.AES_CTR_256, null, encryptedBytes);

        assertThat(localKey.getEncryptedKey()).containsExactly(encryptedBytes);
        assertThat(localKey.getDecryptedKey()).isNull();
    }

    @Test
    void keyMetadataAndKeyProviderKindExposePublicValues() {
        KeyMetadata metadata = new KeyMetadata("customer-key", 42, EncryptionAlgorithm.AES_CTR_128);

        assertThat(metadata.getKeyName()).isEqualTo("customer-key");
        assertThat(metadata.getVersion()).isEqualTo(42);
        assertThat(metadata.getAlgorithm()).isSameAs(EncryptionAlgorithm.AES_CTR_128);
        assertThat(metadata).hasToString("customer-key@42 AES128");

        assertThat(KeyProviderKind.values())
                .containsExactly(
                        KeyProviderKind.UNKNOWN,
                        KeyProviderKind.HADOOP,
                        KeyProviderKind.AWS,
                        KeyProviderKind.GCP,
                        KeyProviderKind.AZURE);
        assertThat(Arrays.stream(KeyProviderKind.values()).mapToInt(KeyProviderKind::getValue).toArray())
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void hadoopShimsWithoutKeyProviderReturnNullKeyProviderBehavior() throws Exception {
        HadoopShims shim = new HadoopShimsPre2_6();
        KeyProvider keyProvider = shim.getHadoopKeyProvider(null, new Random(0));
        KeyMetadata missingKey = new KeyMetadata("missing", 7, EncryptionAlgorithm.AES_CTR_256);

        assertThat(keyProvider.getKeyNames()).isEmpty();
        assertThat(keyProvider.getKind()).isNull();
        assertThat(keyProvider.decryptLocalKey(missingKey, new byte[] {1, 2, 3})).isNull();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> keyProvider.getCurrentKeyVersion("missing"))
                .withMessageContaining("Unknown key missing");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> keyProvider.createLocalKey(missingKey))
                .withMessageContaining("Unknown key missing@7 AES256");
    }

    @Test
    void shimsExposeCompressionTypesAndEndBlockSupport() throws Exception {
        assertThat(DirectCompressionType.values())
                .containsExactly(
                        DirectCompressionType.NONE,
                        DirectCompressionType.ZLIB_NOHEADER,
                        DirectCompressionType.ZLIB,
                        DirectCompressionType.SNAPPY);
        assertShimCompressionSupport(new HadoopShimsPre2_6());
        assertShimCompressionSupport(new HadoopShimsPre2_7());
        assertShimCompressionSupport(new HadoopShimsCurrent());

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(new HadoopShimsPre2_6().endVariableLengthBlock(output)).isFalse();
        assertThat(new HadoopShimsPre2_7().endVariableLengthBlock(output)).isFalse();
        assertThat(new HadoopShimsCurrent().endVariableLengthBlock(output)).isFalse();
    }

    private static void assertAlgorithmRoundTrip(
            EncryptionAlgorithm algorithm, int serialization, int keyLength, String displayName) throws Exception {
        assertThat(algorithm.getAlgorithm()).isEqualTo("AES");
        assertThat(algorithm.getIvLength()).isEqualTo(16);
        assertThat(algorithm.keyLength()).isEqualTo(keyLength);
        assertThat(algorithm.getSerialization()).isEqualTo(serialization);
        assertThat(algorithm.getZeroKey()).hasSize(keyLength).containsOnly((byte) 0);
        assertThat(algorithm).hasToString(displayName);
        assertThat(EncryptionAlgorithm.fromSerialization(serialization)).isSameAs(algorithm);

        byte[] payload = "native-image friendly encryption".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher(algorithm, Cipher.ENCRYPT_MODE).doFinal(payload);
        byte[] decrypted = cipher(algorithm, Cipher.DECRYPT_MODE).doFinal(encrypted);

        assertThat(encrypted).isNotEqualTo(payload);
        assertThat(decrypted).containsExactly(payload);
    }

    private static Cipher cipher(EncryptionAlgorithm algorithm, int mode) throws Exception {
        Cipher cipher = algorithm.createCipher();
        cipher.init(
                mode,
                new SecretKeySpec(algorithm.getZeroKey(), algorithm.getAlgorithm()),
                new IvParameterSpec(new byte[algorithm.getIvLength()]));
        return cipher;
    }

    private static void assertShimCompressionSupport(HadoopShims shim) {
        assertThat(shim.getDirectDecompressor(DirectCompressionType.NONE)).isNull();
    }

    private static byte[] repeatByte(int size, byte value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, value);
        return bytes;
    }
}
