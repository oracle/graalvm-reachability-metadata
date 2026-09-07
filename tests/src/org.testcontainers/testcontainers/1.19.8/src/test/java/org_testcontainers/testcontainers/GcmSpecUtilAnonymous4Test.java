/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import java.security.AlgorithmParameters;
import java.security.Provider;

import javax.crypto.spec.GCMParameterSpec;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.assertj.core.api.Assertions.assertThat;

public class GcmSpecUtilAnonymous4Test {
    @Test
    void extractsTheInitializationVectorFromJdkGcmParameters() throws Exception {
        Provider provider = new BouncyCastleProvider();
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider);
        parameters.init(new GCMParameterSpec(128, new byte[] { 3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8 }));

        GCMParameterSpec restored = parameters.getParameterSpec(GCMParameterSpec.class);

        assertThat(restored.getIV()).containsExactly(3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5, 8);
    }
}
