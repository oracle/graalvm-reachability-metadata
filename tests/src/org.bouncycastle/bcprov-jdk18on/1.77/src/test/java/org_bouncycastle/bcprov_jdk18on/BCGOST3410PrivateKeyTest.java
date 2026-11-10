/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCGOST3410PrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCGOST3410PrivateKey original = (BCGOST3410PrivateKey) KeyPairFixtures.classical("GOST3410").getPrivate();
        assertInstanceOf(BCGOST3410PrivateKey.class, SerializationSupport.persistKey(original));
    }
}
