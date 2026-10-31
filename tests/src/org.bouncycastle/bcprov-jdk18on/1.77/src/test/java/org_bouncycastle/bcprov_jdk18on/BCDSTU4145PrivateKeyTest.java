/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.dstu.BCDSTU4145PrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCDSTU4145PrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCDSTU4145PrivateKey original = (BCDSTU4145PrivateKey) KeyPairFixtures.classical("DSTU4145").getPrivate();
        assertInstanceOf(BCDSTU4145PrivateKey.class, SerializationSupport.persistKey(original));
    }
}
