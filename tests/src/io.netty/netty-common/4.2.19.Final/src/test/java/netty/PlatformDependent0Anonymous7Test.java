/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Anonymous7Test {
    @Test
    void fillsAnUninitializedArrayWithAnEncodedPayload() {
        byte[] payload = PlatformDependent.allocateUninitializedArray(1536);
        for (int i = 0; i < payload.length; i++) {
            payload[i] = (byte) (i * 31);
        }
        Assertions.assertEquals(1536, payload.length);
        byte[] copy = PlatformDependent.allocateUninitializedArray(payload.length);
        System.arraycopy(payload, 0, copy, 0, payload.length);
        Assertions.assertArrayEquals(payload, copy);
        Assertions.assertEquals((byte) (1535 * 31), copy[1535]);
    }
}
