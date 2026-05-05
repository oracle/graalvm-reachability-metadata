/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.MemoryUnmapper;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryUnmapperTest {
    @Test
    void unmapsDirectByteBufferWhenCleanerHackIsEnabled() {
        assertThat(System.getProperty("h2.nioCleanerHack")).isEqualTo("true");

        ByteBuffer directBuffer = ByteBuffer.allocateDirect(16);
        directBuffer.putInt(42);

        MemoryUnmapper.unmap(directBuffer);
    }

    @Test
    void returnsFalseForHeapByteBufferWhenCleanerHackIsEnabled() {
        assertThat(System.getProperty("h2.nioCleanerHack")).isEqualTo("true");

        ByteBuffer heapBuffer = ByteBuffer.allocate(16);
        heapBuffer.putInt(42);

        boolean unmapped = MemoryUnmapper.unmap(heapBuffer);

        assertThat(unmapped).isFalse();
    }
}
