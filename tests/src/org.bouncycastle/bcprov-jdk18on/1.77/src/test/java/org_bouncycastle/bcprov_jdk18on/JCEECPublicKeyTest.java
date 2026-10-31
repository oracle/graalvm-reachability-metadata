/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.interfaces.ECPublicKey;

import org.bouncycastle.jce.provider.JCEECPublicKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JCEECPublicKeyTest {
    @Test
    void persistsImportedPublicKey() throws Exception {
        ECPublicKey generated = (ECPublicKey) KeyPairFixtures.classical("EC").getPublic();
        JCEECPublicKey original = new JCEECPublicKey(generated);
        JCEECPublicKey restored = (JCEECPublicKey) SerializationSupport.persistKey(original);
        assertEquals(generated.getW(), restored.getW());
    }
}
