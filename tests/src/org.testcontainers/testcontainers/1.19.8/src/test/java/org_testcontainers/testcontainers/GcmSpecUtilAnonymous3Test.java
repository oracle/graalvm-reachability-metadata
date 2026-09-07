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

public class GcmSpecUtilAnonymous3Test {
    @Test
    void readsTagLengthFromTheJdkGcmSpecification() throws Exception {
        Provider provider = new BouncyCastleProvider();
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider);
        parameters.init(new GCMParameterSpec(120, new byte[12]));

        GCMParameterSpec restored = parameters.getParameterSpec(GCMParameterSpec.class);

        assertThat(restored.getTLen()).isEqualTo(120);
    }
}
