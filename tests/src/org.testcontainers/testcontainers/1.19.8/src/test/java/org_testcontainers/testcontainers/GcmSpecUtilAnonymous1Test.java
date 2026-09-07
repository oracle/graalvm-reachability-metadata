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

public class GcmSpecUtilAnonymous1Test {
    @Test
    void initializesProviderGcmParametersFromTheJdkSpecification() throws Exception {
        Provider provider = new BouncyCastleProvider();
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider);

        parameters.init(new GCMParameterSpec(96, new byte[12]));

        assertThat(parameters.getEncoded()).isNotEmpty();
    }
}
