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

public class PlatformDependent0Anonymous1Test {
    @Test
    void allocatesWritesAndFreesNativeMemory() {
        long address = PlatformDependent.allocateMemory(16);
        try {
            PlatformDependent.putLong(address, 0x1020304050607080L);
            PlatformDependent.putLong(address + 8, 0x1122334455667788L);
            Assertions.assertEquals(0x1020304050607080L, PlatformDependent.getLong(address));
            Assertions.assertEquals(0x1122334455667788L, PlatformDependent.getLong(address + 8));
        } finally {
            PlatformDependent.freeMemory(address);
        }
    }
}
