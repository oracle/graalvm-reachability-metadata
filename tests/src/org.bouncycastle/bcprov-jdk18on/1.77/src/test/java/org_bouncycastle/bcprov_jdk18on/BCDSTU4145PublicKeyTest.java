/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCDSTU4145PublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCDSTU4145PublicKey original = (BCDSTU4145PublicKey) KeyPairFixtures.classical("DSTU4145").getPublic();
        assertInstanceOf(BCDSTU4145PublicKey.class, SerializationSupport.persistKey(original));
    }
}
