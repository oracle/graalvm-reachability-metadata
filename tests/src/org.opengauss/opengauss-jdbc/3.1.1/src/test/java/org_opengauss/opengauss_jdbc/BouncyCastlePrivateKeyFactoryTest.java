/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfoBuilder;
import org.junit.jupiter.api.Test;
import org.postgresql.ssl.BouncyCastlePrivateKeyFactory;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;

import javax.security.auth.callback.PasswordCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePrivateKeyFactoryTest {
    private static final char[] PASSWORD = "changeit".toCharArray();

    @Test
    void decryptsEncryptedPkcs8PrivateKeyWithBouncyCastle() throws Exception {
        KeyPair keyPair = generateRsaKeyPair();
        byte[] encryptedPrivateKey = encryptPrivateKey(keyPair.getPrivate());

        PasswordCallback passwordCallback = new PasswordCallback("Enter SSL password: ", false);
        passwordCallback.setPassword(PASSWORD);

        PrivateKey decryptedPrivateKey = new BouncyCastlePrivateKeyFactory()
                .getPrivateKeyFromEncryptedKey(encryptedPrivateKey, passwordCallback);

        assertThat(decryptedPrivateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(decryptedPrivateKey.getEncoded()).containsExactly(keyPair.getPrivate().getEncoded());
    }

    private static KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);
        return keyPairGenerator.generateKeyPair();
    }

    private static byte[] encryptPrivateKey(PrivateKey privateKey) throws Exception {
        Provider provider = new BouncyCastleProvider();
        JceOpenSSLPKCS8EncryptorBuilder encryptorBuilder = new JceOpenSSLPKCS8EncryptorBuilder(
                PKCS8Generator.PBE_SHA1_3DES).setProvider(provider).setPassword(PASSWORD).setIterationCount(2048);
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(privateKey.getEncoded());
        return new PKCS8EncryptedPrivateKeyInfoBuilder(privateKeyInfo).build(encryptorBuilder.build()).getEncoded();
    }
}
