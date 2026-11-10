/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.gost.BCGOST3410PublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCGOST3410PublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCGOST3410PublicKey original = (BCGOST3410PublicKey) KeyPairFixtures.classical("GOST3410").getPublic();
        assertInstanceOf(BCGOST3410PublicKey.class, SerializationSupport.persistKey(original));
    }
}
