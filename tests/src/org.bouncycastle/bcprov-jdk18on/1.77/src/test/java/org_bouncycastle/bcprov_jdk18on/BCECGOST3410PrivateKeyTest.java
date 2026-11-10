/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost.BCECGOST3410PrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCECGOST3410PrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCECGOST3410PrivateKey original = (BCECGOST3410PrivateKey) KeyPairFixtures.classical("ECGOST3410").getPrivate();
        assertInstanceOf(BCECGOST3410PrivateKey.class, SerializationSupport.persistKey(original));
    }
}
