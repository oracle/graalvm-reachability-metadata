/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcprov_jdk15to18;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.bouncycastle.pqc.crypto.xmss.XMSSUtil;
import org.junit.jupiter.api.Test;

public class XMSSUtilInnerCheckingStreamTest {
    @Test
    void deserializeIntegerResolvesAllowedPrimaryClass() throws Exception {
        Integer original = Integer.valueOf(123456789);

        byte[] serialized = XMSSUtil.serialize(original);
        Integer restored = (Integer)XMSSUtil.deserialize(serialized, Integer.class);

        assertEquals(original, restored);
    }
}
