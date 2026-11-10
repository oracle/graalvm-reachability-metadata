/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.AlgorithmParameters;

import javax.crypto.spec.IvParameterSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class ClassUtilTest {
    @Test
    void resolvesAesParametersFromLoadedSymmetricMappings() throws Exception {
        // Native Image does not verify a provider created at run time, so this test round-trips
        // the AES IV instead of running an AES/CBC/PKCS7Padding Cipher:
        // https://github.com/oracle/graal/issues/14846 (GR-81035)
        byte[] iv = new byte[16];
        iv[0] = 7;
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("AES", KeyPairFixtures.BC);
        parameters.init(new IvParameterSpec(iv));
        AlgorithmParameters decoded = AlgorithmParameters.getInstance("AES", KeyPairFixtures.BC);
        decoded.init(parameters.getEncoded());
        assertArrayEquals(iv, decoded.getParameterSpec(IvParameterSpec.class).getIV());
    }
}
