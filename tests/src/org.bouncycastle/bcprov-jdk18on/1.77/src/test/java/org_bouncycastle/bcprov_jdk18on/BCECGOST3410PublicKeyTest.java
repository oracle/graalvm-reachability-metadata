/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCECGOST3410PublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCECGOST3410PublicKey original = (BCECGOST3410PublicKey) KeyPairFixtures.classical("ECGOST3410").getPublic();
        assertInstanceOf(BCECGOST3410PublicKey.class, SerializationSupport.persistKey(original));
    }
}
