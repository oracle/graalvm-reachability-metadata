/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk18on;

import java.security.SecureRandom;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.pqc.crypto.xmss.XMSS;
import org.bouncycastle.pqc.crypto.xmss.XMSSParameters;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class XMSSUtilTest {
    @Test
    void resumesSigningFromExportedInitialState() throws Exception {
        XMSSParameters parameters = new XMSSParameters(4, new SHA256Digest());
        XMSS original = new XMSS(parameters, new SecureRandom());
        original.generateKeys();
        byte[] message = new byte[] {1, 3, 5, 7};
        byte[] privateState = original.exportPrivateKey().getEncoded();
        byte[] publicState = original.exportPublicKey().getEncoded();
        XMSS restored = new XMSS(parameters, new SecureRandom());
        restored.importState(privateState, publicState);
        assertArrayEquals(privateState, restored.exportPrivateKey().getEncoded());
        byte[] signature = restored.sign(message);
        assertTrue(restored.verifySignature(message, signature, publicState));
    }
}
