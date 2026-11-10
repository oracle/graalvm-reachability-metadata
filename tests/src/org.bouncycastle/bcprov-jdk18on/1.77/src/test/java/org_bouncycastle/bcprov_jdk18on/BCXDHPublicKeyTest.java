/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.jcajce.provider.asymmetric.edec.BCXDHPublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCXDHPublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCXDHPublicKey original = (BCXDHPublicKey) KeyPairFixtures.classical("X25519").getPublic();
        assertInstanceOf(BCXDHPublicKey.class, SerializationSupport.persistKey(original));
    }
}
