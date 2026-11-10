/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.rainbow.BCRainbowPrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCRainbowPrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCRainbowPrivateKey original = (BCRainbowPrivateKey) KeyPairFixtures.postQuantum("Rainbow").getPrivate();
        assertInstanceOf(BCRainbowPrivateKey.class, SerializationSupport.persistKey(original));
    }
}
