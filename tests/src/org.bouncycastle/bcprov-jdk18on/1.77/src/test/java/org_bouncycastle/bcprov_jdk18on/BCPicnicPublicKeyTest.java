/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.picnic.BCPicnicPublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCPicnicPublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCPicnicPublicKey original = (BCPicnicPublicKey) KeyPairFixtures.postQuantum("Picnic").getPublic();
        assertInstanceOf(BCPicnicPublicKey.class, SerializationSupport.persistKey(original));
    }
}
