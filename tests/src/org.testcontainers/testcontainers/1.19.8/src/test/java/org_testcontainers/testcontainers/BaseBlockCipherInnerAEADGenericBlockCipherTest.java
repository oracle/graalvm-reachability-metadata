/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.Provider;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BaseBlockCipherInnerAEADGenericBlockCipherTest {
    @Test
    void reportsAuthenticationFailureForTamperedGcmCiphertext() throws Exception {
        Provider provider = new BouncyCastleProvider();
        SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
        GCMParameterSpec parameters = new GCMParameterSpec(128, new byte[12]);
        Cipher encryptor = Cipher.getInstance("AES/GCM/NoPadding", provider);
        encryptor.init(Cipher.ENCRYPT_MODE, key, parameters);
        byte[] encrypted = encryptor.doFinal(new byte[] { 1, 2, 3 });
        encrypted[encrypted.length - 1] ^= 1;
        Cipher decryptor = Cipher.getInstance("AES/GCM/NoPadding", provider);
        decryptor.init(Cipher.DECRYPT_MODE, key, parameters);

        assertThatThrownBy(() -> decryptor.doFinal(encrypted)).isInstanceOf(AEADBadTagException.class);
    }
}
