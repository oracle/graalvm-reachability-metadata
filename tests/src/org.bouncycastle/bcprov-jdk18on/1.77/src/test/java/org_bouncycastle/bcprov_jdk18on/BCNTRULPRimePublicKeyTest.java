/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.ntruprime.BCNTRULPRimePublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCNTRULPRimePublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCNTRULPRimePublicKey original = (BCNTRULPRimePublicKey) KeyPairFixtures.postQuantum("NTRULPRime").getPublic();
        assertInstanceOf(BCNTRULPRimePublicKey.class, SerializationSupport.persistKey(original));
    }
}
