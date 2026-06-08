/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.security.AlgorithmParameters;
import java.security.Provider;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilTest {
    private static final byte[] NONCE = new byte[] {
        0x01, 0x02, 0x03, 0x04,
        0x05, 0x06, 0x07, 0x08,
        0x09, 0x0a, 0x0b, 0x0c
    };

    @Test
    void gcmAlgorithmParametersReturnJdkGcmSpec() throws Exception {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("GCM", provider());
        parameters.init(new GCMParameterSpec(96, NONCE));

        GCMParameterSpec extracted = parameters.getParameterSpec(GCMParameterSpec.class);

        assertGcmSpec(extracted, 96);
    }

    @Test
    void asn1EncodedGcmAlgorithmParametersReturnJdkGcmSpec() throws Exception {
        AlgorithmParameters encodedParameters = AlgorithmParameters.getInstance("GCM", provider());
        encodedParameters.init(new GCMParameterSpec(96, NONCE));

        AlgorithmParameters decodedParameters = AlgorithmParameters.getInstance("GCM", provider());
        decodedParameters.init(encodedParameters.getEncoded("ASN.1"), "ASN.1");

        AlgorithmParameterSpec extracted = decodedParameters.getParameterSpec(AlgorithmParameterSpec.class);

        GCMParameterSpec gcmSpec = assertInstanceOf(GCMParameterSpec.class, extracted);
        assertGcmSpec(gcmSpec, 96);
    }

    private static Provider provider() {
        return new BouncyCastleProvider();
    }

    private static void assertGcmSpec(GCMParameterSpec spec, int expectedTagLength) {
        assertEquals(expectedTagLength, spec.getTLen());
        assertArrayEquals(NONCE, spec.getIV());
    }
}
