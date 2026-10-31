/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCEdDSAPrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCEdDSAPrivateKey original = (BCEdDSAPrivateKey) KeyPairFixtures.classical("Ed25519").getPrivate();
        assertInstanceOf(BCEdDSAPrivateKey.class, SerializationSupport.persistKey(original));
    }
}
