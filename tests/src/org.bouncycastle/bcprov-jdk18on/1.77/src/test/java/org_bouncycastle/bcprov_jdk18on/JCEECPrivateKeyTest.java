/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.interfaces.ECPrivateKey;

import org.bouncycastle.jce.provider.JCEECPrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JCEECPrivateKeyTest {
    @Test
    void persistsImportedPrivateKey() throws Exception {
        ECPrivateKey generated = (ECPrivateKey) KeyPairFixtures.classical("EC").getPrivate();
        JCEECPrivateKey original = new JCEECPrivateKey(generated);
        JCEECPrivateKey restored = (JCEECPrivateKey) SerializationSupport.persistKey(original);
        assertEquals(generated.getS(), restored.getS());
    }
}
