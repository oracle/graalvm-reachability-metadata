/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.mceliece.BCMcElieceCCA2PublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCMcElieceCCA2PublicKeyTest {
    @Test
    void persistsGeneratedPublicKey() throws Exception {
        BCMcElieceCCA2PublicKey original = (BCMcElieceCCA2PublicKey) McElieceFixtures.generate().getPublic();
        assertInstanceOf(BCMcElieceCCA2PublicKey.class, SerializationSupport.persistKey(original));
    }
}
