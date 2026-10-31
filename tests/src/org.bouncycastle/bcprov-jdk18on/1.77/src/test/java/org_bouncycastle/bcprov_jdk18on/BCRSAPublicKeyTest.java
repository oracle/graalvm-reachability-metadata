/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCRSAPublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCRSAPublicKey original = (BCRSAPublicKey) KeyPairFixtures.classical("RSASSA-PSS").getPublic();
        assertInstanceOf(BCRSAPublicKey.class, SerializationSupport.persistKey(original));
    }
}
