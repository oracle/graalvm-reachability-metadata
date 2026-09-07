/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.AlgorithmParameters;
import java.security.Provider;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class GcmSpecUtilTest {
    @Test
    void preservesGcmParametersAcrossCipherInitialization() throws Exception {
        Provider provider = new BouncyCastleProvider();
        GCMParameterSpec specification = new GCMParameterSpec(128, new byte[12]);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", provider);
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[16], "AES"), specification);

        AlgorithmParameters parameters = cipher.getParameters();
        GCMParameterSpec restored = parameters.getParameterSpec(GCMParameterSpec.class);

        assertThat(restored.getTLen()).isEqualTo(128);
        assertThat(restored.getIV()).containsExactly(specification.getIV());
    }
}
