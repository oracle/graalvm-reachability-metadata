/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.xmss.BCXMSSPrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCXMSSPrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCXMSSPrivateKey original = (BCXMSSPrivateKey) KeyPairFixtures.postQuantum("XMSS").getPrivate();
        assertInstanceOf(BCXMSSPrivateKey.class, SerializationSupport.persistKey(original));
    }
}
