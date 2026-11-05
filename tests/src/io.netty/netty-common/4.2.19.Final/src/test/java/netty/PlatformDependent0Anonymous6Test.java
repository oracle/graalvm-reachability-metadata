/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.Arrays;

import io.netty.util.internal.PlatformDependent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformDependent0Anonymous6Test {
    @Test
    void allocatesIndependentWritableByteArrays() {
        byte[] first = PlatformDependent.allocateUninitializedArray(4096);
        byte[] second = PlatformDependent.allocateUninitializedArray(4096);
        Arrays.fill(first, (byte) 0x35);
        Arrays.fill(second, (byte) 0x72);
        Assertions.assertEquals(4096, first.length);
        Assertions.assertEquals(4096, second.length);
        Assertions.assertNotSame(first, second);
        for (int i = 0; i < first.length; i++) {
            Assertions.assertEquals((byte) 0x35, first[i]);
            Assertions.assertEquals((byte) 0x72, second[i]);
        }
    }
}
