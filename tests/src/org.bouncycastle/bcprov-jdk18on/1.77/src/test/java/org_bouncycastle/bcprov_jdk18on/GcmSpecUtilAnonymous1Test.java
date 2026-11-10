/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.AlgorithmParameters;

import javax.crypto.spec.GCMParameterSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GcmSpecUtilAnonymous1Test {
    @Test
    void roundTripsCCMParametersWith64BitTag() throws Exception {
        byte[] nonce = new byte[11];
        nonce[0] = 42;
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("CCM", KeyPairFixtures.BC);
        parameters.init(new GCMParameterSpec(64, nonce));
        AlgorithmParameters decoded = AlgorithmParameters.getInstance("CCM", KeyPairFixtures.BC);
        decoded.init(parameters.getEncoded());
        GCMParameterSpec restored = decoded.getParameterSpec(GCMParameterSpec.class);
        assertEquals(64, restored.getTLen());
        assertArrayEquals(nonce, restored.getIV());
    }
}
