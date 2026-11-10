/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.ecgost12.BCECGOST3410_2012PrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCECGOST3410_2012PrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCECGOST3410_2012PrivateKey original = (BCECGOST3410_2012PrivateKey) KeyPairFixtures.classical("ECGOST3410-2012").getPrivate();
        assertInstanceOf(BCECGOST3410_2012PrivateKey.class, SerializationSupport.persistKey(original));
    }
}
