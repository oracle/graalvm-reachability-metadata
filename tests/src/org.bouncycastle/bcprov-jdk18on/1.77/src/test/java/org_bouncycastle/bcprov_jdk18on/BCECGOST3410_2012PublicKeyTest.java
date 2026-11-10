/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost12.BCECGOST3410_2012PublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCECGOST3410_2012PublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCECGOST3410_2012PublicKey original = (BCECGOST3410_2012PublicKey) KeyPairFixtures.classical("ECGOST3410-2012").getPublic();
        assertInstanceOf(BCECGOST3410_2012PublicKey.class, SerializationSupport.persistKey(original));
    }
}
