/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;

import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;
import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.provider.util.SpecUtil;
import org.junit.jupiter.api.Test;

public class SpecUtilAnonymous1Test {
    @Test
    void getNameFromReadsBouncyCastleSpecGetNameMethod() {
        ECNamedCurveGenParameterSpec parameterSpec = new ECNamedCurveGenParameterSpec("bike128");

        String name = SpecUtil.getNameFrom(parameterSpec);

        assertEquals("bike128", name);
    }

    @Test
    void keyPairGeneratorAcceptsBouncyCastleSpecWithGetNameMethod() throws Exception {
        BouncyCastlePQCProvider provider = new BouncyCastlePQCProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("BIKE", provider);
        ECNamedCurveGenParameterSpec parameterSpec = new ECNamedCurveGenParameterSpec("bike128");

        assertDoesNotThrow(() -> generator.initialize(parameterSpec, new SecureRandom()));
        KeyPair keyPair = generator.generateKeyPair();

        assertEquals("BIKE128", keyPair.getPublic().getAlgorithm());
        assertEquals("BIKE128", keyPair.getPrivate().getAlgorithm());
    }
}
