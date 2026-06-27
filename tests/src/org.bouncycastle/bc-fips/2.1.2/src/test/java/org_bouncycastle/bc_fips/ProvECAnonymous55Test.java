/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bc_fips;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.security.AlgorithmParameters;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.NamedParameterSpec;

import org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ProvECAnonymous55Test {
    private static final String NATIVE_CPU_VARIANT_PROPERTY = "org.bouncycastle.native.cpu_variant";
    private static final String P256_OID = "1.2.840.10045.3.1.7";

    @BeforeAll
    static void useJavaOnlyNativeVariant() {
        System.setProperty(NATIVE_CPU_VARIANT_PROPERTY, "java");
    }

    @Test
    void algorithmParametersInitializesEcCurveFromNamedParameterSpec() throws Exception {
        Provider provider = bouncyCastleFipsProvider();
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC", provider);

        parameters.init(new NamedParameterSpec("P-256"));

        ECGenParameterSpec parameterSpec = parameters.getParameterSpec(ECGenParameterSpec.class);
        assertNotNull(parameters.getEncoded());
        assertEquals(P256_OID, parameterSpec.getName());
    }

    @Test
    void keyPairGeneratorInitializesEcCurveFromNamedParameterSpec() throws Exception {
        Provider provider = bouncyCastleFipsProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", provider);

        generator.initialize(new NamedParameterSpec("P-256"), new SecureRandom(new byte[] {1, 2, 3, 4}));
        KeyPair keyPair = generator.generateKeyPair();

        assertEquals("EC", keyPair.getPrivate().getAlgorithm());
        assertEquals("EC", keyPair.getPublic().getAlgorithm());
    }

    private static Provider bouncyCastleFipsProvider() {
        Provider provider = Security.getProvider(BouncyCastleFipsProvider.PROVIDER_NAME);
        if (provider != null) {
            return provider;
        }
        return new BouncyCastleFipsProvider();
    }
}
