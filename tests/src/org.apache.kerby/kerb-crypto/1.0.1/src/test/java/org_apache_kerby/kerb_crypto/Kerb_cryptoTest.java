/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_crypto;

import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.crypto.CheckSumHandler;
import org.apache.kerby.kerberos.kerb.crypto.CheckSumTypeHandler;
import org.apache.kerby.kerberos.kerb.crypto.EncTypeHandler;
import org.apache.kerby.kerberos.kerb.crypto.EncryptionHandler;
import org.apache.kerby.kerberos.kerb.crypto.enc.EncryptProvider;
import org.apache.kerby.kerberos.kerb.crypto.enc.provider.Aes128Provider;
import org.apache.kerby.kerberos.kerb.crypto.fast.FastUtil;
import org.apache.kerby.kerberos.kerb.crypto.util.BytesUtil;
import org.apache.kerby.kerberos.kerb.crypto.util.Crc32;
import org.apache.kerby.kerberos.kerb.crypto.util.Nfold;
import org.apache.kerby.kerberos.kerb.crypto.util.Nonce;
import org.apache.kerby.kerberos.kerb.crypto.util.Pbkdf;
import org.apache.kerby.kerberos.kerb.crypto.util.Random;
import org.apache.kerby.kerberos.kerb.type.base.CheckSum;
import org.apache.kerby.kerberos.kerb.type.base.CheckSumType;
import org.apache.kerby.kerberos.kerb.type.base.EncryptedData;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionKey;
import org.apache.kerby.kerberos.kerb.type.base.EncryptionType;
import org.apache.kerby.kerberos.kerb.type.base.KeyUsage;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Kerb_cryptoTest {
    private static final byte[] MESSAGE = "Kerby crypto integration test payload".getBytes(StandardCharsets.UTF_8);
    private static final String PASSWORD = "correct horse battery staple";
    private static final String SALT = "EXAMPLE.COMintegration";

    @Test
    void resolvesImplementedEncryptionHandlersByNameValueAndEnum() throws Exception {
        for (EncryptionType encryptionType : new EncryptionType[] {
                EncryptionType.AES128_CTS_HMAC_SHA1_96,
                EncryptionType.AES256_CTS_HMAC_SHA1_96,
                EncryptionType.CAMELLIA128_CTS_CMAC,
                EncryptionType.CAMELLIA256_CTS_CMAC,
                EncryptionType.ARCFOUR_HMAC
        }) {
            EncTypeHandler byEnum = EncryptionHandler.getEncHandler(encryptionType);
            EncTypeHandler byName = EncryptionHandler.getEncHandler(encryptionType.getName());
            EncTypeHandler byValue = EncryptionHandler.getEncHandler(encryptionType.getValue());

            assertThat(EncryptionHandler.isImplemented(encryptionType)).isTrue();
            assertThat(byEnum.eType()).isEqualTo(encryptionType);
            assertThat(byName.eType()).isEqualTo(encryptionType);
            assertThat(byValue.eType()).isEqualTo(encryptionType);
            assertThat(byEnum.keySize()).isPositive();
            assertThat(byEnum.confounderSize()).isNotNegative();
            assertThat(byEnum.checksumType()).isNotNull();
        }

        assertThat(EncryptionHandler.isImplemented(EncryptionType.NONE)).isFalse();
        assertThatThrownBy(() -> EncryptionHandler.getEncHandler(EncryptionType.NONE))
                .isInstanceOf(KrbException.class)
                .hasMessageContaining("Unsupported encryption type");
    }

    @Test
    void derivesStringKeysUsingKerberosTestVectors() throws Exception {
        EncryptionKey aes128 = EncryptionHandler.string2Key(
                "password",
                "ATHENA.MIT.EDUraeburn",
                hex("00000002"),
                EncryptionType.AES128_CTS_HMAC_SHA1_96);
        EncryptionKey aes256 = EncryptionHandler.string2Key(
                "password",
                "ATHENA.MIT.EDUraeburn",
                hex("00000002"),
                EncryptionType.AES256_CTS_HMAC_SHA1_96);

        assertThat(aes128.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(aes128.getKeyData()).containsExactly(hex("C651BF29E2300AC27FA469D693BDDA13"));
        assertThat(aes256.getKeyType()).isEqualTo(EncryptionType.AES256_CTS_HMAC_SHA1_96);
        assertThat(aes256.getKeyData()).containsExactly(hex(
                "A2E16D16B36069C135D5E9D2E25F896102685618B95914B467C67622225824FF"));
    }

    @Test
    void encryptsAndDecryptsPayloadsWithHighLevelEncryptionHandler() throws Exception {
        for (EncryptionType encryptionType : new EncryptionType[] {
                EncryptionType.AES128_CTS_HMAC_SHA1_96,
                EncryptionType.AES256_CTS_HMAC_SHA1_96,
                EncryptionType.CAMELLIA128_CTS_CMAC,
                EncryptionType.ARCFOUR_HMAC
        }) {
            EncryptionKey key = EncryptionHandler.string2Key(PASSWORD, SALT, encryptionType);
            key.setKvno(7);

            EncryptedData encryptedData = EncryptionHandler.encrypt(MESSAGE, key, KeyUsage.APP_DATA_ENCRYPT);
            byte[] decrypted = EncryptionHandler.decrypt(encryptedData, key, KeyUsage.APP_DATA_ENCRYPT);
            byte[] decryptedFromCipher = EncryptionHandler.decrypt(
                    encryptedData.getCipher(),
                    key,
                    KeyUsage.APP_DATA_ENCRYPT);

            assertThat(encryptedData.getEType()).isEqualTo(encryptionType);
            assertThat(encryptedData.getKvno()).isEqualTo(7);
            assertThat(encryptedData.getCipher()).isNotEmpty();
            assertThat(Arrays.equals(encryptedData.getCipher(), MESSAGE)).isFalse();
            assertThat(decrypted).containsExactly(MESSAGE);
            assertThat(decryptedFromCipher).containsExactly(MESSAGE);
        }
    }

    @Test
    void computesAndVerifiesUnkeyedAndKeyedChecksums() throws Exception {
        CheckSum crc32 = CheckSumHandler.checksum(CheckSumType.CRC32, MESSAGE);
        CheckSum md5 = CheckSumHandler.checksum(CheckSumType.RSA_MD5, MESSAGE);
        byte[] tamperedMessage = MESSAGE.clone();
        tamperedMessage[0] ^= 0x01;

        assertThat(CheckSumHandler.isImplemented(CheckSumType.CRC32)).isTrue();
        assertThat(crc32.getCksumtype()).isEqualTo(CheckSumType.CRC32);
        assertThat(crc32.getChecksum()).containsExactly(hex("C10E2ED3"));
        assertThat(CheckSumHandler.verify(crc32, MESSAGE)).isTrue();
        assertThat(CheckSumHandler.verify(md5, MESSAGE)).isTrue();
        assertThat(CheckSumHandler.verify(md5, tamperedMessage)).isFalse();

        EncryptionKey key = EncryptionHandler.string2Key(PASSWORD, SALT, EncryptionType.AES128_CTS_HMAC_SHA1_96);
        CheckSum keyed = CheckSumHandler.checksumWithKey(
                CheckSumType.HMAC_SHA1_96_AES128,
                MESSAGE,
                key.getKeyData(),
                KeyUsage.APP_DATA_CKSUM);
        byte[] corruptedChecksum = keyed.getChecksum().clone();
        corruptedChecksum[0] ^= 0x01;

        assertThat(keyed.getCksumtype()).isEqualTo(CheckSumType.HMAC_SHA1_96_AES128);
        assertThat(keyed.getChecksum()).hasSize(12);
        assertThat(CheckSumHandler.verifyWithKey(keyed, MESSAGE, key.getKeyData(), KeyUsage.APP_DATA_CKSUM)).isTrue();
        assertThat(CheckSumHandler.verifyWithKey(
                new CheckSum(CheckSumType.HMAC_SHA1_96_AES128, corruptedChecksum),
                MESSAGE,
                key.getKeyData(),
                KeyUsage.APP_DATA_CKSUM)).isFalse();
    }

    @Test
    void exposesChecksumHandlerCharacteristics() throws Exception {
        CheckSumTypeHandler crc32 = CheckSumHandler.getCheckSumHandler(CheckSumType.CRC32);
        CheckSumTypeHandler aes128 = CheckSumHandler.getCheckSumHandler(CheckSumType.HMAC_SHA1_96_AES128.getValue());
        CheckSumTypeHandler byName = CheckSumHandler.getCheckSumHandler(CheckSumType.HMAC_SHA1_96_AES128.getName());

        assertThat(crc32.cksumType()).isEqualTo(CheckSumType.CRC32);
        assertThat(crc32.isSafe()).isFalse();
        assertThat(crc32.outputSize()).isEqualTo(4);
        assertThat(aes128.cksumType()).isEqualTo(CheckSumType.HMAC_SHA1_96_AES128);
        assertThat(aes128.isSafe()).isTrue();
        assertThat(aes128.keySize()).isEqualTo(16);
        assertThat(byName.cksumType()).isEqualTo(CheckSumType.HMAC_SHA1_96_AES128);

        assertThat(CheckSumHandler.isImplemented(CheckSumType.NONE)).isFalse();
        assertThatThrownBy(() -> CheckSumHandler.getCheckSumHandler(CheckSumType.NONE))
                .isInstanceOf(KrbException.class)
                .hasMessageContaining("Unsupported checksum type");
    }

    @Test
    void derivesFastArmorReplyAndPrfBytes() throws Exception {
        EncryptionKey key1 = EncryptionHandler.string2Key("key1", "key1", EncryptionType.AES128_CTS_HMAC_SHA1_96);
        EncryptionKey key2 = EncryptionHandler.string2Key("key2", "key2", EncryptionType.AES128_CTS_HMAC_SHA1_96);

        EncryptionKey combined = FastUtil.cf2(key1, "a", key2, "b");
        EncryptionKey replyKey = FastUtil.makeReplyKey(key1, key2);
        EncryptionKey armorKey = FastUtil.makeArmorKey(key1, key2);
        byte[] prfPlus = FastUtil.prfPlus(key1, "subkey seed", 40);

        assertThat(combined.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(combined.getKeyData()).containsExactly(hex("97DF97E4B798B29EB31ED7280287A92A"));
        assertThat(replyKey.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(replyKey.getKeyData()).hasSize(16);
        assertThat(armorKey.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(armorKey.getKeyData()).hasSize(16);
        assertThat(armorKey.getKeyData()).isNotEqualTo(replyKey.getKeyData());
        assertThat(prfPlus).hasSize(40);
        assertThat(Arrays.copyOf(prfPlus, 16)).isNotEqualTo(Arrays.copyOfRange(prfPlus, 16, 32));
    }

    @Test
    void lowerLevelAesProviderEncryptsInPlaceAndWithSeparateBuffers() throws Exception {
        EncryptProvider provider = new Aes128Provider();
        byte[] key = hex("000102030405060708090A0B0C0D0E0F");
        byte[] plainBlock = hex("00112233445566778899AABBCCDDEEFF");
        byte[] cipherState = new byte[provider.blockSize()];
        byte[] cipherBlock = plainBlock.clone();

        provider.encrypt(key, cipherState, cipherBlock);
        assertThat(cipherBlock).containsExactly(hex("69C4E0D86A7B0430D8CDB78070B4C55A"));

        byte[] decryptedBlock = cipherBlock.clone();
        provider.decrypt(key, cipherState, decryptedBlock);
        assertThat(decryptedBlock).containsExactly(plainBlock);

        byte[] inPlace = plainBlock.clone();
        provider.encrypt(key, inPlace);
        assertThat(inPlace).containsExactly(cipherBlock);
        provider.decrypt(key, inPlace);
        assertThat(inPlace).containsExactly(plainBlock);
        assertThat(provider.supportCbcMac()).isFalse();
    }

    @Test
    void utilityMethodsHandleEndianPaddingRandomAndDerivationOperations() throws Exception {
        byte[] bigEndianInt = BytesUtil.int2bytes(0x01020304, true);
        byte[] littleEndianInt = BytesUtil.int2bytes(0x01020304, false);
        byte[] padded = BytesUtil.padding(new byte[] {1, 2, 3}, 8);
        byte[] duplicate = BytesUtil.duplicate(new byte[] {9, 8, 7, 6}, 1, 2);
        byte[] xorInput = new byte[] {1, 2, 3, 4};
        byte[] xorOutput = new byte[] {4, 3, 2, 1};

        BytesUtil.xor(xorInput, 0, xorOutput);

        assertThat(bigEndianInt).containsExactly(new byte[] {1, 2, 3, 4});
        assertThat(littleEndianInt).containsExactly(new byte[] {4, 3, 2, 1});
        assertThat(BytesUtil.bytes2int(bigEndianInt, true)).isEqualTo(0x01020304);
        assertThat(BytesUtil.bytes2int(littleEndianInt, false)).isEqualTo(0x01020304);
        assertThat(padded).containsExactly(new byte[] {1, 2, 3, 0, 0, 0, 0, 0});
        assertThat(duplicate).containsExactly(new byte[] {8, 7});
        assertThat(xorOutput).containsExactly(new byte[] {5, 1, 1, 5});
        assertThat(Crc32.crc(0, "foo".getBytes(StandardCharsets.UTF_8), 0, 3)).isEqualTo(0x7332bc33L);
        assertThat(Pbkdf.pbkdf2(
                "password".toCharArray(),
                "salt".getBytes(StandardCharsets.UTF_8),
                1,
                20)).containsExactly(hex("0C60C80F961F0E71F3A9B524AF6012062FE037A6"));
        assertThat(Nfold.nfold("kerberos".getBytes(StandardCharsets.UTF_8), 16)).hasSize(16);
        assertThat(Random.makeBytes(24)).hasSize(24);
        assertThat(Nonce.value()).isNotNegative();
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }
}
