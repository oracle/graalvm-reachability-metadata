/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.Provider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class GcmSpecUtilAnonymous3Test {
    @Test
    void encryptsWithTagLengthReadFromTheJdkGcmSpecification() throws Exception {
        Provider provider = new BouncyCastleProvider();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        cipher.init(
            Cipher.ENCRYPT_MODE,
            new SecretKeySpec(new byte[16], "AES"),
            new GCMParameterSpec(128, new byte[12])
        );

        assertThat(cipher.doFinal(new byte[] { 1, 2, 3 })).hasSize(19);
    }
}
