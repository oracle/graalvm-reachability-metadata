/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECParameterSpec;

import org.bouncycastle.jcajce.provider.asymmetric.util.ECUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveGenParameterSpec;
import org.junit.jupiter.api.Test;

public class ECUtilAnonymous1Test {
    private static final String CURVE_NAME = "secp256r1";

    @Test
    void getNameFromReadsBouncyCastleNamedCurveParameterSpec() {
        ECNamedCurveGenParameterSpec parameterSpec =
            new ECNamedCurveGenParameterSpec(CURVE_NAME);

        String name = ECUtil.getNameFrom(parameterSpec);

        assertEquals(CURVE_NAME, name);
    }

    @Test
    void keyPairGeneratorAcceptsParameterSpecWithGetNameMethod() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
            "EC", new BouncyCastleProvider());

        keyPairGenerator.initialize(new NamedCurveParameterSpec(CURVE_NAME));
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        assertEquals("EC", keyPair.getPublic().getAlgorithm());
        assertEquals("EC", keyPair.getPrivate().getAlgorithm());
        ECParameterSpec publicParameters = ((ECPublicKey)keyPair.getPublic()).getParams();
        ECParameterSpec privateParameters = ((ECPrivateKey)keyPair.getPrivate()).getParams();
        assertNotNull(publicParameters);
        assertNotNull(privateParameters);
        assertEquals(publicParameters.getOrder(), privateParameters.getOrder());
        assertEquals(publicParameters.getGenerator(), privateParameters.getGenerator());
    }

    public static final class NamedCurveParameterSpec implements AlgorithmParameterSpec {
        private final String name;

        public NamedCurveParameterSpec(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
