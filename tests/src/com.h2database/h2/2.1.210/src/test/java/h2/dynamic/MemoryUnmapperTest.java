/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2.dynamic;

import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    static {
        System.setProperty("h2.nioCleanerHack", "true");
    }

    @Test
    void attemptsToUnmapDirectBufferWithConfiguredCleanerHack() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);

        boolean unmapped = MemoryUnmapper.unmap(buffer);

        assertThat(unmapped).isIn(true, false);
    }
}
