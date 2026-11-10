/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCECPublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCECPublicKey original = (BCECPublicKey) KeyPairFixtures.classical("EC").getPublic();
        assertInstanceOf(BCECPublicKey.class, SerializationSupport.persistKey(original));
    }
}
