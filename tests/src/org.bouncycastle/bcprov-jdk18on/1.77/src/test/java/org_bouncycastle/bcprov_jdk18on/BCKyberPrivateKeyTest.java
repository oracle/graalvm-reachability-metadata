/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.kyber.BCKyberPrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCKyberPrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCKyberPrivateKey original = (BCKyberPrivateKey) KeyPairFixtures.postQuantum("Kyber").getPrivate();
        assertInstanceOf(BCKyberPrivateKey.class, SerializationSupport.persistKey(original));
    }
}
