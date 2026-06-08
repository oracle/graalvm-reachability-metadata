/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.security.spec.InvalidParameterSpecException;

import javax.crypto.spec.GCMParameterSpec;

import org.bouncycastle.internal.asn1.cms.GCMParameters;
import org.bouncycastle.jcajce.provider.symmetric.util.GcmSpecUtil;
import org.junit.jupiter.api.Test;

public class GcmSpecUtilAnonymous4Test {
    private static final byte[] NONCE = new byte[] {
        0x41, 0x42, 0x43, 0x44,
        0x45, 0x46, 0x47, 0x48,
        0x49, 0x4a, 0x4b, 0x4c
    };

    @Test
    void extractsGcmParametersFromJdkGcmParameterSpec() throws Exception {
        GCMParameterSpec parameterSpec = new GCMParameterSpec(120, NONCE);

        try {
            GCMParameters parameters = GcmSpecUtil.extractGcmParameters(parameterSpec);

            assertEquals(15, parameters.getIcvLen());
            assertArrayEquals(NONCE, parameters.getNonce());
        } catch (InvalidParameterSpecException exception) {
            assertFalse(GcmSpecUtil.gcmSpecExtractable());
        }
    }
}
