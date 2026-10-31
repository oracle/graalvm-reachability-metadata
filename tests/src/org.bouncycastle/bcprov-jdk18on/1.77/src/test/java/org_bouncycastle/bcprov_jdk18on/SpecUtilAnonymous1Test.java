/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpecUtilAnonymous1Test {
    @Test
    void generatesPostQuantumSigningKeyFromNamedSpecification() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("SPHINCSPLUS", KeyPairFixtures.PQC);
        generator.initialize(new NamedParameterSpec("sha2-128f-robust"));
        KeyPair pair = generator.generateKeyPair();
        Signature signer = Signature.getInstance("SPHINCSPLUS", KeyPairFixtures.PQC);
        signer.initSign(pair.getPrivate());
        signer.update(new byte[] {4, 5, 6});
        byte[] signature = signer.sign();
        signer.initVerify(pair.getPublic());
        signer.update(new byte[] {4, 5, 6});
        assertTrue(signer.verify(signature));
    }
}
