/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Provider;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.pqc.jcajce.provider.BouncyCastlePQCProvider;
import org.bouncycastle.pqc.jcajce.spec.KyberParameterSpec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class BouncyCastlePQCProviderTest {
    @Test
    void loadsPostQuantumGeneratorsAndFactories() throws Exception {
        Provider provider = new BouncyCastlePQCProvider();
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Kyber", provider);
        generator.initialize(KyberParameterSpec.kyber512);
        KeyPair pair = generator.generateKeyPair();
        KeyFactory factory = KeyFactory.getInstance("Kyber", provider);
        assertArrayEquals(pair.getPublic().getEncoded(),
                factory.generatePublic(new X509EncodedKeySpec(pair.getPublic().getEncoded())).getEncoded());
    }
}
