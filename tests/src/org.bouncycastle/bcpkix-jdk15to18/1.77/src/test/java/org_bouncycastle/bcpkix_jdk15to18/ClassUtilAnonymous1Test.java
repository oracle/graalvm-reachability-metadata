/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcpkix_jdk15to18;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.its.jcajce.JceETSIDataEncryptor;
import org.bouncycastle.jcajce.spec.AEADParameterSpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class ClassUtilAnonymous1Test {

    @Test
    void reflectedGcmParametersProduceDecryptableItsCiphertext() throws Exception {
        Provider provider = new BouncyCastleProvider();
        JceETSIDataEncryptor encryptor = new JceETSIDataEncryptor.Builder()
                .setProvider(provider)
                .build();
        byte[] plaintext = "cooperative awareness message".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = encryptor.encrypt(plaintext);
        Cipher decryptor = Cipher.getInstance("CCM", provider);
        decryptor.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(encryptor.getKey(), "AES"),
                new AEADParameterSpec(encryptor.getNonce(), 128));

        assertThat(decryptor.doFinal(ciphertext)).containsExactly(plaintext);
    }
}
