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

public class GcmSpecUtilAnonymous2Test {
    @Test
    void readsProviderGcmParametersAsTheJdkSpecification() throws Exception {
        Provider provider = new BouncyCastleProvider();
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider);
        GCMParameterSpec original = new GCMParameterSpec(104, new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        parameters.init(original);

        GCMParameterSpec restored = parameters.getParameterSpec(GCMParameterSpec.class);

        assertThat(restored.getTLen()).isEqualTo(104);
        assertThat(restored.getIV()).containsExactly(original.getIV());
    }
}
