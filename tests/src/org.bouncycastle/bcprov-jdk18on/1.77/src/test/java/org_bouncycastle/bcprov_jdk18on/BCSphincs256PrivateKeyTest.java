/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PrivateKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class BCSphincs256PrivateKeyTest {
    @Test
    void persistsGeneratedPrivateKey() throws Exception {
        BCSphincs256PrivateKey original = (BCSphincs256PrivateKey) KeyPairFixtures.postQuantum("SPHINCS256").getPrivate();
        assertInstanceOf(BCSphincs256PrivateKey.class, SerializationSupport.persistKey(original));
    }
}
