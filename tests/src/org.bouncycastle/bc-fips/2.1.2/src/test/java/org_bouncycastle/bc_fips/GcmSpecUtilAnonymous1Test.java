/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import java.security.AlgorithmParameters;
import java.security.Security;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GcmSpecUtilAnonymous1Test {
    private static final byte[] NONCE = new byte[] {
        16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27
    };

    @BeforeAll
    static void registerProvider() {
        Security.addProvider(new BouncyCastleFipsProvider());
    }

    @Test
    void restoresGcmParameterSpecFromEncodedParameters() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance(
            "GCM", BouncyCastleFipsProvider.PROVIDER_NAME);
        parameters.init(new GCMParameterSpec(128, NONCE));

        AlgorithmParameters restoredParameters = AlgorithmParameters.getInstance(
            "GCM", BouncyCastleFipsProvider.PROVIDER_NAME);
        restoredParameters.init(parameters.getEncoded());

        GCMParameterSpec restoredSpec = restoredParameters.getParameterSpec(GCMParameterSpec.class);

        assertThat(restoredSpec.getTLen()).isEqualTo(128);
        assertThat(restoredSpec.getIV()).isEqualTo(NONCE);
    }
}
