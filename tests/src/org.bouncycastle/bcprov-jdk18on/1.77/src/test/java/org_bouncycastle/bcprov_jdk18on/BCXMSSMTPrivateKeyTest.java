/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.xmss.BCXMSSMTPrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCXMSSMTPrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCXMSSMTPrivateKey original = (BCXMSSMTPrivateKey) KeyPairFixtures.postQuantum("XMSSMT").getPrivate();
        assertInstanceOf(BCXMSSMTPrivateKey.class, SerializationSupport.persistKey(original));
    }
}
