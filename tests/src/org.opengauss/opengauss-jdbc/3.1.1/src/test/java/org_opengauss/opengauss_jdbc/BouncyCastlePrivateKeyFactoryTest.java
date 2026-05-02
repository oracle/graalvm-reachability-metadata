/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jcajce.util.ProviderJcaJceHelper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder;
import org.junit.jupiter.api.Test;
import org.postgresql.ssl.BouncyCastlePrivateKeyFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;

import javax.security.auth.callback.PasswordCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePrivateKeyFactoryTest {
    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void decryptsBouncyCastleEncryptedPkcs8PrivateKey() throws Exception {
        Provider provider = ensureBouncyCastleProvider();
        KeyPair keyPair = generateRsaKeyPair(provider);
        byte[] encryptedKey = encryptPrivateKey(provider, keyPair.getPrivate());
        PasswordCallback passwordCallback = new PasswordCallback("Private key password", false);
        passwordCallback.setPassword(Arrays.copyOf(PASSWORD, PASSWORD.length));

        try {
            PrivateKey privateKey = new BouncyCastlePrivateKeyFactory()
                    .getPrivateKeyFromEncryptedKey(encryptedKey, passwordCallback);

            assertThat(privateKey.getAlgorithm()).isEqualTo(keyPair.getPrivate().getAlgorithm());
            assertThat(privateKey.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
        } finally {
            passwordCallback.clearPassword();
        }
    }

    private static Provider ensureBouncyCastleProvider() {
        Provider provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
        if (provider == null) {
            provider = new BouncyCastleProvider();
            Security.addProvider(provider);
        }
        return provider;
    }

    private static KeyPair generateRsaKeyPair(Provider provider) throws Exception {
        ProviderJcaJceHelper helper = new ProviderJcaJceHelper(provider);
        KeyPairGenerator keyPairGenerator = helper.createKeyPairGenerator("RSA");
        keyPairGenerator.initialize(1024, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }

    private static byte[] encryptPrivateKey(Provider provider, PrivateKey privateKey) throws Exception {
        OutputEncryptor encryptor = new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.PBE_SHA1_3DES)
                .setProvider(provider)
                .setRandom(new SecureRandom())
                .setPasssword(PASSWORD)
                .build();
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(privateKey.getEncoded());
        PKCS8EncryptedPrivateKeyInfo encryptedPrivateKeyInfo = new PKCS8EncryptedPrivateKeyInfoBuilder(privateKeyInfo)
                .build(encryptor);
        return encryptedPrivateKeyInfo.getEncoded();
    }
}
